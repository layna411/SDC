from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

import cv2
import numpy as np

from app.core.config import settings
from app.models.enums import ImageQualityStatus


@dataclass
class PreprocessedImageResult:
    valid: bool
    width: int
    height: int
    quality_status: ImageQualityStatus
    quality_metrics: Dict[str, Any]
    normalized_tensor: Optional[np.ndarray] = None
    error_message: Optional[str] = None


class OralImagePreprocessor:
    """Validates, assesses quality, and preprocesses clinical oral images using OpenCV."""

    TARGET_SIZE: Tuple[int, int] = (224, 224)

    def process(self, image_path: Path) -> PreprocessedImageResult:
        if not image_path.exists():
            return PreprocessedImageResult(
                valid=False,
                width=0,
                height=0,
                quality_status=ImageQualityStatus.INVALID,
                quality_metrics={"reason": "Image file not found."},
                error_message="Image file not found on storage.",
            )

        img_bgr = cv2.imread(str(image_path))
        if img_bgr is None or img_bgr.size == 0:
            return PreprocessedImageResult(
                valid=False,
                width=0,
                height=0,
                quality_status=ImageQualityStatus.INVALID,
                quality_metrics={"reason": "Unable to decode clinical image."},
                error_message="Uploaded file could not be decoded as a valid image.",
            )

        height, width = img_bgr.shape[:2]
        gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)

        # Real OpenCV objective image quality metrics (sharpness, brightness, contrast)
        laplacian_var = float(cv2.Laplacian(gray, cv2.CV_64F).var())
        mean_brightness = float(np.mean(gray))
        contrast_std = float(np.std(gray))

        quality_status = ImageQualityStatus.ACCEPTABLE
        quality_notes = []

        if width < settings.AI_MIN_IMAGE_WIDTH or height < settings.AI_MIN_IMAGE_HEIGHT:
            quality_status = ImageQualityStatus.LOW_RESOLUTION
            quality_notes.append(
                f"Resolution {width}x{height} below recommended minimum "
                f"{settings.AI_MIN_IMAGE_WIDTH}x{settings.AI_MIN_IMAGE_HEIGHT}."
            )
        elif mean_brightness < 35.0 or mean_brightness > 235.0:
            quality_status = ImageQualityStatus.POOR_LIGHTING
            quality_notes.append(
                f"Mean illumination {mean_brightness:.1f} is outside optimal clinical capture range (35-235)."
            )
        elif laplacian_var < 25.0:
            quality_status = ImageQualityStatus.BLURRY
            quality_notes.append(
                f"Focus sharpness metric ({laplacian_var:.1f}) indicates potential motion blur."
            )

        # Standard RGB color-space conversion, CLAHE lighting normalization & resizing for downstream models
        img_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
        lab = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2LAB)
        l_ch, a_ch, b_ch = cv2.split(lab)
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        cl = clahe.apply(l_ch)
        enhanced_rgb = cv2.cvtColor(cv2.merge((cl, a_ch, b_ch)), cv2.COLOR_LAB2RGB)
        resized = cv2.resize(enhanced_rgb, self.TARGET_SIZE, interpolation=cv2.INTER_AREA)
        normalized = resized.astype(np.float32) / 255.0

        metrics = {
            "width_px": width,
            "height_px": height,
            "sharpness_laplacian_var": round(laplacian_var, 2),
            "mean_brightness": round(mean_brightness, 2),
            "contrast_std": round(contrast_std, 2),
            "preprocessed_shape": list(normalized.shape),
            "notes": quality_notes or ["Image meets technical capture quality criteria."],
        }

        return PreprocessedImageResult(
            valid=True,
            width=width,
            height=height,
            quality_status=quality_status,
            quality_metrics=metrics,
            normalized_tensor=normalized,
        )


oral_image_preprocessor = OralImagePreprocessor()
