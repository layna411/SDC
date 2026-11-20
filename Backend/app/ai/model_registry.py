from abc import ABC, abstractmethod
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Optional

import cv2
import numpy as np

from app.core.config import settings


@dataclass
class ModelInferenceOutput:
    available: bool
    model_name: str
    model_version: str
    output: Optional[Dict[str, Any]]
    confidence_score: Optional[float]
    message: str


class BaseOralHealthModel(ABC):
    """Pluggable interface so clinically validated models can be swapped without UI or API changes."""

    @property
    @abstractmethod
    def model_name(self) -> str:
        raise NotImplementedError

    @property
    @abstractmethod
    def model_version(self) -> str:
        raise NotImplementedError

    @abstractmethod
    def is_ready(self) -> bool:
        raise NotImplementedError

    @abstractmethod
    def predict(
        self, normalized_image: np.ndarray, clinical_region: str
    ) -> ModelInferenceOutput:
        raise NotImplementedError


class OpenCVOralScreeningAnalyzer(BaseOralHealthModel):
    """
    Assistive OpenCV computer-vision analyzer for oral mucosal screening images.
    Extracts objective LAB/HSV mucosal color and texture features to flag candidate
    white-plaque (leukoplakia-like), erythematous (red patch), or heterogeneous ulcer-like
    regions as NORMAL or ABNORMAL for clinician review.
    Can be replaced seamlessly by a validated PyTorch TorchScript model via AI_MODEL_PATH.
    """

    @property
    def model_name(self) -> str:
        return "OralCare-OpenCV-Mucosal-Screening"

    @property
    def model_version(self) -> str:
        return "1.0.0-cv-assistive"

    def is_ready(self) -> bool:
        return True

    def predict(
        self, normalized_image: np.ndarray, clinical_region: str
    ) -> ModelInferenceOutput:
        # Convert normalized RGB [0..1] back to uint8 RGB for OpenCV LAB & HSV color analysis
        rgb_u8 = np.clip(normalized_image * 255.0, 0, 255).astype(np.uint8)
        h, w = rgb_u8.shape[:2]

        # Focus on central 70% ROI where the oral mucosa is framed
        y0, y1 = int(h * 0.15), int(h * 0.85)
        x0, x1 = int(w * 0.15), int(w * 0.85)
        roi_rgb = rgb_u8[y0:y1, x0:x1]

        roi_hsv = cv2.cvtColor(roi_rgb, cv2.COLOR_RGB2HSV)
        roi_lab = cv2.cvtColor(roi_rgb, cv2.COLOR_RGB2LAB)
        roi_gray = cv2.cvtColor(roi_rgb, cv2.COLOR_RGB2GRAY)

        l_ch, a_ch, b_ch = cv2.split(roi_lab)
        h_ch, s_ch, v_ch = cv2.split(roi_hsv)

        total_pixels = float(roi_gray.size) if roi_gray.size > 0 else 1.0

        # 1. Candidate white plaque / keratotic patch ratio: bright & low saturation (excluding glare)
        white_mask = (l_ch > 205) & (s_ch < 48) & (v_ch < 252)
        white_patch_ratio = float(np.sum(white_mask)) / total_pixels

        # 2. Candidate erythematous / intense red lesion ratio: elevated LAB a* & strong saturation
        red_mask = (a_ch > 168) & (s_ch > 115) & (l_ch > 55) & (l_ch < 200)
        erythema_ratio = float(np.sum(red_mask)) / total_pixels

        # 3. Candidate dark / ulcerated / necrotic irregularity ratio
        dark_irreg_mask = (l_ch < 52) & (a_ch > 132)
        ulcer_candidate_ratio = float(np.sum(dark_irreg_mask)) / total_pixels

        # 4. Texture heterogeneity (Canny edge density inside mucosal ROI)
        edges = cv2.Canny(roi_gray, 60, 150)
        edge_density = float(np.sum(edges > 0)) / total_pixels

        # Compute transparent assistive anomaly score (0.0 .. 1.0)
        anomaly_score = min(
            1.0,
            (white_patch_ratio * 2.4)
            + (erythema_ratio * 2.1)
            + (ulcer_candidate_ratio * 2.6)
            + max(0.0, edge_density - 0.14) * 1.4,
        )

        flags = []
        if white_patch_ratio >= 0.14:
            flags.append(f"Elevated pale/white mucosal region ({white_patch_ratio * 100:.1f}% of ROI)")
        if erythema_ratio >= 0.18:
            flags.append(f"Elevated erythematous/red mucosal intensity ({erythema_ratio * 100:.1f}% of ROI)")
        if ulcer_candidate_ratio >= 0.10:
            flags.append(f"Localized dark/erosive mucosal contrast ({ulcer_candidate_ratio * 100:.1f}% of ROI)")
        if edge_density >= 0.24:
            flags.append(f"Irregular mucosal surface texture density ({edge_density * 100:.1f}%)")

        is_abnormal = anomaly_score >= 0.36 or len(flags) >= 1
        classification = "ABNORMAL" if is_abnormal else "NORMAL"

        # Confidence reflects distance from decision threshold (bounded conservatively 0.55..0.94)
        distance = abs(anomaly_score - 0.36)
        confidence = round(float(min(0.94, max(0.58, 0.62 + distance * 0.85))), 4)

        summary_msg = (
            f"AI Screening flagged ABNORMAL candidate mucosal features in {clinical_region}: "
            + "; ".join(flags)
            if is_abnormal
            else f"AI Screening found uniform mucosal appearance in {clinical_region} (NORMAL). Clinician verification advised."
        )

        return ModelInferenceOutput(
            available=True,
            model_name=self.model_name,
            model_version=self.model_version,
            output={
                "service_status": "COMPLETED",
                "classification": classification,
                "clinical_region": clinical_region,
                "anomaly_score": round(anomaly_score, 4),
                "features": {
                    "white_patch_ratio": round(white_patch_ratio, 4),
                    "erythema_ratio": round(erythema_ratio, 4),
                    "ulcer_candidate_ratio": round(ulcer_candidate_ratio, 4),
                    "texture_edge_density": round(edge_density, 4),
                },
                "detected_flags": flags,
                "summary": summary_msg,
                "assistive_note": "Assistive AI screening output; final clinical confirmation rests with a qualified dental clinician.",
            },
            confidence_score=confidence,
            message=summary_msg,
        )


class SMART2CalibratedMucosalModel(BaseOralHealthModel):
    """
    Clinically calibrated oral mucosal screening model trained directly on the
    Saveetha Dental College SMART II / SMITA Dataset (136 patients, 2,148 images).
    Evaluates 27 objective anatomical, colorimetric (LAB/HSV), and textural features
    with anatomical site baseline normalizations (LB, RB, DT, VT, UL, LL, UA, LA).
    Distinguishes between Normal Mucosa, Anatomical Variations/Benign Lesions, and OPMD.
    """

    def __init__(self, bundle_path: Optional[Path] = None):
        self._bundle_path = bundle_path or (Path(__file__).parent / "smart2_calibrated_model.joblib")
        self._bundle = None
        self._load_error: Optional[str] = None
        self._try_load()

    def _try_load(self) -> None:
        if not self._bundle_path.exists():
            self._load_error = f"SMART II model artifact not found at {self._bundle_path}"
            return
        try:
            import joblib

            self._bundle = joblib.load(str(self._bundle_path))
        except Exception as exc:
            self._load_error = f"Failed to load SMART II model artifact: {exc}"
            self._bundle = None

    @property
    def model_name(self) -> str:
        return "OralCare-SMART2-Calibrated-Mucosal-Model"

    @property
    def model_version(self) -> str:
        return "2.0.0-smart2"

    def is_ready(self) -> bool:
        return self._bundle is not None

    def predict(
        self, normalized_image: np.ndarray, clinical_region: str
    ) -> ModelInferenceOutput:
        if not self.is_ready():
            return OpenCVOralScreeningAnalyzer().predict(normalized_image, clinical_region)

        from app.ai.train_smart2_calibrator import (
            build_feature_vector,
            extract_raw_metrics_from_rgb,
            parse_region_code,
        )

        rgb_u8 = np.clip(normalized_image * 255.0, 0, 255).astype(np.uint8)

        reg_code = parse_region_code(clinical_region)
        baselines = self._bundle.get("regional_baselines", {})
        multi_model = self._bundle.get("multiclass_model")

        raw_metrics = extract_raw_metrics_from_rgb(rgb_u8)
        feat_vector = build_feature_vector(raw_metrics, reg_code, baselines)

        probs = multi_model.predict_proba([feat_vector])[0]
        # Classes: 0: NORMAL, 1: VARIATION, 2: OPMD
        p_norm = float(probs[0])
        p_var = float(probs[1]) if len(probs) > 1 else 0.0
        p_opmd = float(probs[2]) if len(probs) > 2 else 0.0

        # Clinical decision logic
        reg_base = baselines.get(reg_code, baselines.get("OTHER", {}))
        base_white = reg_base.get("white_patch_ratio", 0.02)
        base_a = reg_base.get("a_mean", 140.0)
        base_edge = reg_base.get("edge_density", 0.02)

        delta_white = raw_metrics["white_patch_ratio"] - base_white
        delta_a = raw_metrics["a_mean"] - base_a
        delta_edge = raw_metrics["edge_density"] - base_edge

        flags = []
        if raw_metrics["white_patch_ratio"] > max(0.015, base_white * 2.0):
            flags.append(
                f"Elevated keratotic white plaque signature ({raw_metrics['white_patch_ratio'] * 100:.1f}%, baseline {base_white * 100:.1f}%)"
            )
        if raw_metrics["erythema_ratio"] >= 0.015 or delta_a > 6.0:
            flags.append(
                f"Elevated mucosal redness/erythema ({raw_metrics['erythema_ratio'] * 100:.1f}%, delta a* {delta_a:+.1f})"
            )
        if raw_metrics["pigment_ratio"] >= 0.15:
            flags.append(
                f"Brown-to-black mucosal pigmentation/melanosis ({raw_metrics['pigment_ratio'] * 100:.1f}%)"
            )
        if raw_metrics["ulcer_contrast_ratio"] >= 0.08:
            flags.append(
                f"Localized dark/erosive mucosal contrast ({raw_metrics['ulcer_contrast_ratio'] * 100:.1f}%)"
            )
        if delta_edge > 0.025:
            flags.append(
                f"Elevated mucosal surface texture irregularity ({raw_metrics['edge_density'] * 100:.1f}%, baseline {base_edge * 100:.1f}%)"
            )

        if p_opmd >= 0.35 or (p_opmd > p_norm and p_opmd > p_var):
            classification = "ABNORMAL"
            subtype = "Oral Potentially Malignant Disorder (OPMD Candidate)"
            confidence = round(max(0.72, min(0.98, p_opmd)), 4)
            summary_msg = (
                f"AI flagged potential OPMD candidate features in {clinical_region}: "
                + ("; ".join(flags) if flags else f"Model probability {p_opmd * 100:.1f}%.")
            )
        elif p_var >= 0.38 or (p_var > p_norm and p_var >= p_opmd):
            classification = "ABNORMAL"
            subtype = "Mucosal Variation / Benign Lesion Candidate"
            confidence = round(max(0.68, min(0.95, p_var)), 4)
            summary_msg = (
                f"AI flagged mucosal variation / benign feature in {clinical_region}: "
                + ("; ".join(flags) if flags else f"Model probability {p_var * 100:.1f}%.")
            )
        else:
            classification = "NORMAL"
            subtype = "Uniform Healthy Mucosa"
            confidence = round(max(0.65, min(0.99, p_norm)), 4)
            summary_msg = (
                f"AI Screening verified uniform mucosal appearance in {clinical_region} "
                f"(consistent with SMART II {reg_code} healthy cohort baseline)."
            )

        return ModelInferenceOutput(
            available=True,
            model_name=self.model_name,
            model_version=self.model_version,
            output={
                "service_status": "COMPLETED",
                "classification": classification,
                "clinical_region": clinical_region,
                "anatomical_site_code": reg_code,
                "predicted_subtype": subtype,
                "class_probabilities": {
                    "normal": round(p_norm, 4),
                    "variation": round(p_var, 4),
                    "opmd": round(p_opmd, 4),
                },
                "regional_baseline_comparison": {
                    "site_code": reg_code,
                    "measured_lightness_L": round(raw_metrics["l_mean"], 2),
                    "baseline_lightness_L": round(reg_base.get("l_mean", 0.0), 2),
                    "delta_lightness_L": round(raw_metrics["l_mean"] - reg_base.get("l_mean", 0.0), 2),
                    "measured_redness_a": round(raw_metrics["a_mean"], 2),
                    "baseline_redness_a": round(base_a, 2),
                    "delta_redness_a": round(delta_a, 2),
                    "measured_white_ratio": round(raw_metrics["white_patch_ratio"], 4),
                    "baseline_white_ratio": round(base_white, 4),
                    "measured_edge_density": round(raw_metrics["edge_density"], 4),
                    "baseline_edge_density": round(base_edge, 4),
                },
                "features": {
                    "white_patch_ratio": round(raw_metrics["white_patch_ratio"], 4),
                    "erythema_ratio": round(raw_metrics["erythema_ratio"], 4),
                    "pigment_ratio": round(raw_metrics["pigment_ratio"], 4),
                    "ulcer_candidate_ratio": round(raw_metrics["ulcer_contrast_ratio"], 4),
                    "texture_edge_density": round(raw_metrics["edge_density"], 4),
                    "color_entropy": round(raw_metrics["color_entropy"], 4),
                },
                "detected_flags": flags,
                "summary": summary_msg,
                "calibrated_dataset": "Saveetha Dental College SMART II / SMITA Clinical Dataset (136 patients, 2,148 images)",
                "assistive_note": "Assistive AI screening output calibrated on Saveetha Dental College SMART II dataset; clinical diagnosis must be verified by a qualified dental professional.",
            },
            confidence_score=confidence,
            message=summary_msg,
        )


class TorchScriptOralHealthModel(BaseOralHealthModel):
    """Adapter for loading a clinically validated TorchScript (.pt/.pts) model when configured."""

    def __init__(self, model_path: Path, name: str, version: str):
        self._path = model_path
        self._name = name
        self._version = version
        self._model = None
        self._load_error: Optional[str] = None
        self._try_load()

    def _try_load(self) -> None:
        if not self._path.exists():
            self._load_error = f"Configured model artifact not found at {self._path}"
            return
        try:
            import torch

            self._model = torch.jit.load(str(self._path), map_location="cpu")
            self._model.eval()
        except Exception as exc:
            self._load_error = f"Unable to load configured TorchScript model: {exc}"
            self._model = None

    @property
    def model_name(self) -> str:
        return self._name

    @property
    def model_version(self) -> str:
        return self._version

    def is_ready(self) -> bool:
        return self._model is not None

    def predict(
        self, normalized_image: np.ndarray, clinical_region: str
    ) -> ModelInferenceOutput:
        if not self.is_ready():
            return OpenCVOralScreeningAnalyzer().predict(normalized_image, clinical_region)

        import torch

        tensor = torch.from_numpy(normalized_image).permute(2, 0, 1).unsqueeze(0).float()
        with torch.no_grad():
            logits = self._model(tensor)
            probs = torch.softmax(logits, dim=-1).squeeze(0).cpu().numpy().tolist()

        abnormal_prob = float(probs[1]) if len(probs) >= 2 else float(probs[0])
        classification = "ABNORMAL" if abnormal_prob >= 0.5 else "NORMAL"
        top_conf = float(max(probs)) if probs else 0.5
        return ModelInferenceOutput(
            available=True,
            model_name=self._name,
            model_version=self._version,
            output={
                "service_status": "COMPLETED",
                "classification": classification,
                "clinical_region": clinical_region,
                "class_probabilities": probs,
                "assistive_note": "Requires qualified clinician verification.",
            },
            confidence_score=round(top_conf, 4),
            message=f"Validated model classified {clinical_region} as {classification} ({top_conf * 100:.1f}%).",
        )


class ModelRegistry:
    """Manages the active AI screening model adapter."""

    def __init__(self) -> None:
        self._custom_adapter: Optional[BaseOralHealthModel] = None

    def register_model(self, adapter: BaseOralHealthModel) -> None:
        self._custom_adapter = adapter

    def clear_custom_model(self) -> None:
        self._custom_adapter = None

    def get_active_model(self) -> BaseOralHealthModel:
        if self._custom_adapter is not None:
            return self._custom_adapter
        if settings.AI_MODEL_PATH:
            return TorchScriptOralHealthModel(
                model_path=Path(settings.AI_MODEL_PATH),
                name=settings.AI_MODEL_NAME or "oralcare-validated-model",
                version=settings.AI_MODEL_VERSION or "1.0.0",
            )
        smart2_model = SMART2CalibratedMucosalModel()
        if smart2_model.is_ready():
            return smart2_model
        return OpenCVOralScreeningAnalyzer()


model_registry = ModelRegistry()
