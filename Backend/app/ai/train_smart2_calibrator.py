"""
Train and calibrate oral mucosal screening model on Saveetha Dental College SMART II / SMITA Dataset.
Produces: Backend/app/ai/smart2_calibrated_model.joblib
Uses the exact same CLAHE + 224x224 RGB preprocessing as oral_image_preprocessor.
"""

import math
import sys
import zipfile
from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, List, Tuple

import cv2
import joblib
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report

REGION_MAP = {
    "LB": 0,
    "RB": 1,
    "DT": 2,
    "VT": 3,
    "UL": 4,
    "LL": 5,
    "UA": 6,
    "LA": 7,
    "OTHER": 8,
}

FEATURE_NAMES = [
    "l_mean",
    "l_std",
    "l_p10",
    "l_p90",
    "a_mean",
    "a_std",
    "a_p10",
    "a_p90",
    "b_mean",
    "b_std",
    "h_mean",
    "s_mean",
    "s_std",
    "v_mean",
    "v_std",
    "white_patch_ratio",
    "erythema_ratio",
    "pigment_ratio",
    "ulcer_contrast_ratio",
    "edge_density",
    "laplacian_var",
    "color_entropy",
    "region_idx",
    "delta_l_baseline",
    "delta_a_baseline",
    "delta_edge_baseline",
    "delta_white_baseline",
]


def parse_region_code(filename_or_text: str) -> str:
    s = filename_or_text.upper()
    for code in ["LB", "RB", "DT", "VT", "UL", "LL", "UA", "LA"]:
        if (
            f"_{code}." in s
            or f"_{code}_" in s
            or f"_{code}1." in s
            or f"_{code}2." in s
            or f"_{code}3." in s
            or f"_{code} " in s
        ):
            return code
    if "BUCCAL" in s:
        if "LEFT" in s or " LB" in s:
            return "LB"
        if "RIGHT" in s or " RB" in s:
            return "RB"
    if "TONGUE" in s:
        if "DORSAL" in s or "DORSUM" in s:
            return "DT"
        if "VENTRAL" in s:
            return "VT"
    if "LABIAL" in s or "LIP" in s:
        if "LOWER" in s:
            return "LL"
        if "UPPER" in s:
            return "UL"
    if "ALVEOLAR" in s:
        if "LOWER" in s:
            return "LA"
        if "UPPER" in s:
            return "UA"
    return "OTHER"


def preprocess_bgr_to_rgb224(img_bgr: np.ndarray) -> np.ndarray:
    """Matches the exact transformation in OralImagePreprocessor."""
    img_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
    lab = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2LAB)
    l_ch, a_ch, b_ch = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    cl = clahe.apply(l_ch)
    enhanced_rgb = cv2.cvtColor(cv2.merge((cl, a_ch, b_ch)), cv2.COLOR_LAB2RGB)
    resized_rgb = cv2.resize(enhanced_rgb, (224, 224), interpolation=cv2.INTER_AREA)
    return resized_rgb


def compute_color_entropy(img_rgb: np.ndarray) -> float:
    entropy = 0.0
    for ch in range(3):
        hist, _ = np.histogram(img_rgb[:, :, ch], bins=32, range=(0, 256), density=True)
        hist = hist[hist > 0]
        entropy -= float(np.sum(hist * np.log2(hist)))
    return round(entropy, 4)


def extract_raw_metrics_from_rgb(rgb_u8: np.ndarray) -> Dict[str, float]:
    """Extracts mucosal features from 224x224 RGB image (or central mucosal ROI)."""
    h, w = rgb_u8.shape[:2]
    # Mucosal central focus (70% ROI)
    y0, y1 = int(h * 0.15), int(h * 0.85)
    x0, x1 = int(w * 0.15), int(w * 0.85)
    roi_rgb = rgb_u8[y0:y1, x0:x1] if (h >= 100 and w >= 100) else rgb_u8

    roi_lab = cv2.cvtColor(roi_rgb, cv2.COLOR_RGB2LAB)
    roi_hsv = cv2.cvtColor(roi_rgb, cv2.COLOR_RGB2HSV)
    roi_gray = cv2.cvtColor(roi_rgb, cv2.COLOR_RGB2GRAY)

    l_ch, a_ch, b_ch = cv2.split(roi_lab)
    h_ch, s_ch, v_ch = cv2.split(roi_hsv)
    tot = float(roi_gray.size) if roi_gray.size > 0 else 1.0

    white_mask = (l_ch > 185) & (s_ch < 60) & (v_ch > 165)
    red_mask = (a_ch > 160) & (s_ch > 100) & (l_ch > 60) & (l_ch < 210)
    pigment_mask = (l_ch < 75) & (a_ch > 125) & (b_ch > 125)
    ulcer_mask = (l_ch < 50) & (a_ch > 130)
    edges = cv2.Canny(roi_gray, 50, 140)

    return {
        "l_mean": float(np.mean(l_ch)),
        "l_std": float(np.std(l_ch)),
        "l_p10": float(np.percentile(l_ch, 10)),
        "l_p90": float(np.percentile(l_ch, 90)),
        "a_mean": float(np.mean(a_ch)),
        "a_std": float(np.std(a_ch)),
        "a_p10": float(np.percentile(a_ch, 10)),
        "a_p90": float(np.percentile(a_ch, 90)),
        "b_mean": float(np.mean(b_ch)),
        "b_std": float(np.std(b_ch)),
        "h_mean": float(np.mean(h_ch)),
        "s_mean": float(np.mean(s_ch)),
        "s_std": float(np.std(s_ch)),
        "v_mean": float(np.mean(v_ch)),
        "v_std": float(np.std(v_ch)),
        "white_patch_ratio": float(np.sum(white_mask)) / tot,
        "erythema_ratio": float(np.sum(red_mask)) / tot,
        "pigment_ratio": float(np.sum(pigment_mask)) / tot,
        "ulcer_contrast_ratio": float(np.sum(ulcer_mask)) / tot,
        "edge_density": float(np.sum(edges > 0)) / tot,
        "laplacian_var": float(cv2.Laplacian(roi_gray, cv2.CV_64F).var()),
        "color_entropy": compute_color_entropy(roi_rgb),
    }


def build_feature_vector(
    raw: Dict[str, float], region_code: str, regional_baselines: Dict[str, Dict[str, float]]
) -> List[float]:
    base = regional_baselines.get(region_code, regional_baselines.get("OTHER", {}))
    base_l = base.get("l_mean", 120.0)
    base_a = base.get("a_mean", 140.0)
    base_edge = base.get("edge_density", 0.02)
    base_white = base.get("white_patch_ratio", 0.02)

    delta_l = raw["l_mean"] - base_l
    delta_a = raw["a_mean"] - base_a
    delta_edge = raw["edge_density"] - base_edge
    delta_white = (raw["white_patch_ratio"] - base_white) / max(0.005, base_white)

    reg_idx = float(REGION_MAP.get(region_code, REGION_MAP["OTHER"]))

    vec = [
        raw["l_mean"],
        raw["l_std"],
        raw["l_p10"],
        raw["l_p90"],
        raw["a_mean"],
        raw["a_std"],
        raw["a_p10"],
        raw["a_p90"],
        raw["b_mean"],
        raw["b_std"],
        raw["h_mean"],
        raw["s_mean"],
        raw["s_std"],
        raw["v_mean"],
        raw["v_std"],
        raw["white_patch_ratio"],
        raw["erythema_ratio"],
        raw["pigment_ratio"],
        raw["ulcer_contrast_ratio"],
        raw["edge_density"],
        raw["laplacian_var"],
        raw["color_entropy"],
        reg_idx,
        delta_l,
        delta_a,
        delta_edge,
        delta_white,
    ]
    return vec


def augment_rgb224(rgb_u8: np.ndarray) -> List[np.ndarray]:
    """Clinical augmentations in RGB 224x224 space."""
    variants = [rgb_u8]
    # Horizontal flip
    variants.append(cv2.flip(rgb_u8, 1))
    # Light intensity variations
    for factor in [0.93, 1.07]:
        adj = np.clip(rgb_u8.astype(np.float32) * factor, 0, 255).astype(np.uint8)
        variants.append(adj)
    # Slight center crop & resize
    h, w = rgb_u8.shape[:2]
    crop = rgb_u8[int(h * 0.05) : int(h * 0.95), int(w * 0.05) : int(w * 0.95)]
    variants.append(cv2.resize(crop, (w, h)))
    return variants


def train():
    zip_path = Path("SMART II Dataset.zip")
    if not zip_path.exists():
        print(f"Error: {zip_path} not found.")
        sys.exit(1)

    print("Opening SMART II Dataset.zip...")
    with zipfile.ZipFile(zip_path, "r") as z:
        all_names = z.namelist()
        normal_files = [
            f
            for f in all_names
            if "01. Normal" in f
            and "/Unannotated/" in f
            and f.lower().endswith((".jpg", ".jpeg", ".png"))
        ]
        var_files = [
            f
            for f in all_names
            if "02. Variation" in f
            and "/Unannotated/" in f
            and f.lower().endswith((".jpg", ".jpeg", ".png"))
        ]
        opmd_files = [
            f
            for f in all_names
            if "03. OPMD" in f
            and "/Unannotated/" in f
            and f.lower().endswith((".jpg", ".jpeg", ".png"))
        ]

        print(
            f"Dataset images: Normal={len(normal_files)}, Variation={len(var_files)}, OPMD={len(opmd_files)}"
        )

        # Step 1: Preprocess Normal images & Compute Regional Baselines
        print("Preprocessing normal images and computing regional baselines...")
        region_metrics = defaultdict(lambda: defaultdict(list))
        normal_cache = []

        for f in normal_files:
            reg = parse_region_code(f)
            data = z.read(f)
            arr = np.frombuffer(data, np.uint8)
            img_bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
            if img_bgr is None:
                continue
            rgb224 = preprocess_bgr_to_rgb224(img_bgr)
            raw = extract_raw_metrics_from_rgb(rgb224)
            normal_cache.append((f, reg, raw))
            for k, val in raw.items():
                region_metrics[reg][k].append(val)

        regional_baselines: Dict[str, Dict[str, float]] = {}
        for reg, metric_dict in region_metrics.items():
            regional_baselines[reg] = {
                k: round(float(np.mean(vals)), 4) for k, vals in metric_dict.items()
            }
            regional_baselines[reg]["count"] = len(metric_dict["l_mean"])

        all_l = [raw["l_mean"] for _, _, raw in normal_cache]
        all_a = [raw["a_mean"] for _, _, raw in normal_cache]
        all_edge = [raw["edge_density"] for _, _, raw in normal_cache]
        all_white = [raw["white_patch_ratio"] for _, _, raw in normal_cache]
        regional_baselines["OTHER"] = {
            "l_mean": round(float(np.mean(all_l)), 4),
            "a_mean": round(float(np.mean(all_a)), 4),
            "edge_density": round(float(np.mean(all_edge)), 4),
            "white_patch_ratio": round(float(np.mean(all_white)), 4),
            "count": len(normal_cache),
        }

        # Step 2: Build Training Feature Matrix
        X = []
        y_multi = []  # 0: Normal, 1: Variation, 2: OPMD
        y_bin = []  # 0: Normal, 1: Abnormal

        for _, reg, raw in normal_cache:
            vec = build_feature_vector(raw, reg, regional_baselines)
            X.append(vec)
            y_multi.append(0)
            y_bin.append(0)

        for f in var_files:
            reg = parse_region_code(f)
            data = z.read(f)
            arr = np.frombuffer(data, np.uint8)
            img_bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
            if img_bgr is None:
                continue
            rgb224 = preprocess_bgr_to_rgb224(img_bgr)
            for aug_rgb in augment_rgb224(rgb224):
                raw = extract_raw_metrics_from_rgb(aug_rgb)
                vec = build_feature_vector(raw, reg, regional_baselines)
                X.append(vec)
                y_multi.append(1)
                y_bin.append(1)

        for f in opmd_files:
            reg = parse_region_code(f)
            data = z.read(f)
            arr = np.frombuffer(data, np.uint8)
            img_bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
            if img_bgr is None:
                continue
            rgb224 = preprocess_bgr_to_rgb224(img_bgr)
            for aug_rgb in augment_rgb224(rgb224):
                raw = extract_raw_metrics_from_rgb(aug_rgb)
                vec = build_feature_vector(raw, reg, regional_baselines)
                X.append(vec)
                y_multi.append(2)
                y_bin.append(1)
            hflip = cv2.flip(rgb224, 1)
            for factor in [0.88, 1.12]:
                adj = np.clip(hflip.astype(np.float32) * factor, 0, 255).astype(np.uint8)
                raw = extract_raw_metrics_from_rgb(adj)
                vec = build_feature_vector(raw, reg, regional_baselines)
                X.append(vec)
                y_multi.append(2)
                y_bin.append(1)

        X = np.array(X, dtype=np.float32)
        y_multi = np.array(y_multi, dtype=np.int32)
        y_bin = np.array(y_bin, dtype=np.int32)

        print(f"Training dataset size: {X.shape[0]} samples, {X.shape[1]} features")

        # Step 3: Train Balanced Random Forest Models
        print("Fitting Screening Model (Normal vs Abnormal)...")
        rf_screen = RandomForestClassifier(
            n_estimators=100,
            max_depth=10,
            class_weight="balanced",
            random_state=42,
        )
        rf_screen.fit(X, y_bin)

        print("Fitting Multi-Class Model (Normal vs Variation vs OPMD)...")
        rf_multi = RandomForestClassifier(
            n_estimators=120,
            max_depth=10,
            class_weight="balanced_subsample",
            random_state=42,
        )
        rf_multi.fit(X, y_multi)

        preds_multi = rf_multi.predict(X)
        print("\nMulti-Class Training Set Performance Report:")
        print(classification_report(y_multi, preds_multi, target_names=["NORMAL", "VARIATION", "OPMD"]))

        # Step 4: Serialize Model Bundle
        out_path = Path("Backend/app/ai/smart2_calibrated_model.joblib")
        bundle = {
            "screening_model": rf_screen,
            "multiclass_model": rf_multi,
            "regional_baselines": regional_baselines,
            "feature_names": FEATURE_NAMES,
            "region_map": REGION_MAP,
            "class_names": ["NORMAL", "VARIATION", "OPMD"],
            "dataset_info": {
                "name": "Saveetha Dental College SMART II / SMITA Dataset",
                "patient_count": 136,
                "clean_images_analyzed": len(normal_files) + len(var_files) + len(opmd_files),
                "trained_samples": len(y_multi),
                "calibrated_regions": list(regional_baselines.keys()),
            },
        }
        joblib.dump(bundle, out_path, compress=3)
        print(f"Calibrated model bundle saved to {out_path} ({out_path.stat().st_size} bytes)")


if __name__ == "__main__":
    train()
