package com.simats.sdc.core.common

data class AnatomicalRegionSpec(
    val code: String,
    val name: String,
    val subRegions: List<String>,
    val referenceGuide: String,
)

object ClinicalConstants {
    val PREFIXES = listOf("Mr.", "Mrs.", "Ms.", "Dr.", "Master", "Baby")
    val SEX_OPTIONS = listOf("MALE", "FEMALE", "OTHER")
    val RELIGIONS = listOf("Hindu", "Muslim", "Christian", "Sikh", "Buddhist", "Jain", "Other", "Prefer not to say")
    val MARITAL_STATUSES = listOf("Single", "Married", "Widowed", "Divorced", "Separated")
    val EDUCATION_LEVELS = listOf("No Formal Education", "Primary", "Secondary", "Higher Secondary", "Diploma", "Graduate", "Postgraduate")
    val INCOME_BRACKETS = listOf("< ₹10,000", "₹10,000 – ₹25,000", "₹25,000 – ₹50,000", "₹50,000 – ₹1,00,000", "> ₹1,00,000")

    val DIET_HISTORIES = listOf("Vegetarian", "Non-Vegetarian", "Mixed", "Eggetarian")
    val SPICE_LEVELS = listOf("Mild", "Moderate", "High", "Very High")

    val HABIT_CATEGORIES = listOf("TOBACCO", "ARECANUT", "ALCOHOL", "OTHERS")
    val HABIT_USAGE_STATUSES = listOf("CURRENT", "PAST")

    val CLEANING_AIDS = listOf("Tooth Brush", "Electronic", "Finger")
    val CLEANING_MATERIALS = listOf("Tooth Paste", "Tooth Powder", "Charcoal", "Natural Twigs", "Other")
    val BRUSHING_METHODS = listOf("Horizontal", "Vertical", "Circular / Modified Bass", "Combined")
    val BRUSHING_FREQUENCIES = listOf("Once daily", "Twice daily", "More than twice daily", "Occasional")
    val BRUSHING_DURATIONS = listOf("< 1 minute", "1–2 minutes", "2–3 minutes", "> 3 minutes")
    val TOOTHBRUSH_CHANGE_FREQUENCIES = listOf("Every 1 month", "Every 3 months", "Every 6 months", "More than 6 months")
    val OTHER_ORAL_HYGIENE_AIDS = listOf("None", "Dental Floss", "Mouthwash", "Interdental Brush", "Tongue Cleaner", "Water Flosser")

    val MEDICAL_CONDITIONS = listOf(
        "No Relevant History",
        "Diabetes",
        "Thyroid Disorders",
        "Anemia",
        "Hypertension",
        "Cancer",
        "Tuberculosis",
        "Genetic Disorder",
        "Any Other",
    )

    val ANATOMICAL_REGIONS = listOf(
        AnatomicalRegionSpec("LABIAL_MUCOSA", "Labial Mucosa", listOf("Upper", "Lower"), "Retract upper and lower lips gently to inspect inner labial mucosa and vestibule."),
        AnatomicalRegionSpec("BUCCAL_MUCOSA", "Buccal Mucosa", listOf("Right", "Left"), "Use mouth mirror to inspect right and left cheeks from commissure to retromolar trigone."),
        AnatomicalRegionSpec("PALATAL_MUCOSA", "Palatal Mucosa", listOf("Hard Palate", "Soft Palate"), "Inspect anterior rugae, hard palate vault, and mobile soft palate."),
        AnatomicalRegionSpec("ALVEOLAR_MUCOSA", "Alveolar Mucosa", listOf("Right", "Left"), "Examine maxillary and mandibular alveolar ridges and gingiva bilaterally."),
        AnatomicalRegionSpec("TONGUE", "Tongue", listOf("Dorsal", "Lateral", "Ventral", "Anterior", "Posterior", "Right", "Left"), "Inspect dorsum, grasp gently with gauze to inspect lateral borders and ventral surface."),
        AnatomicalRegionSpec("FLOOR_OF_MOUTH", "Floor of the Mouth", listOf("General"), "Ask patient to touch palate with tongue tip; examine sublingual area and Wharton's duct."),
        AnatomicalRegionSpec("PALATINE_TONSIL", "Palatine Tonsil", listOf("Right", "Left"), "Depress posterior tongue gently to inspect tonsillar fossae bilaterally."),
        AnatomicalRegionSpec("PILLAR_OF_FAUCES", "Pillar of Fauces", listOf("Anterior", "Posterior"), "Evaluate anterior palatoglossal and posterior palatopharyngeal arches."),
        AnatomicalRegionSpec("FRENIAL_ATTACHMENT", "Frenial Attachment", listOf("Maxillary", "Lingual", "Lower"), "Check maxillary labial, lingual, and lower labial frenum attachments."),
        AnatomicalRegionSpec("ANGLE_OF_MOUTH", "Angle of the Mouth", listOf("Right", "Left"), "Inspect oral commissures bilaterally for fissuring, maceration, or plaques."),
        AnatomicalRegionSpec("LIP_VERMILLION_BORDER", "Lip and Vermillion Border", listOf("Upper", "Lower"), "Examine upper and lower vermillion zones and mucocutaneous junction."),
    )

    val CLINICAL_OBSERVATION_OPTIONS = listOf(
        "WHITE_PATCH" to "White patch / plaque observation",
        "RED_PATCH" to "Red / erythematous patch observation",
        "MIXED_RED_WHITE" to "Mixed red-and-white mucosal change",
        "ULCERATION" to "Ulceration / mucosal erosion",
        "SWELLING_GROWTH" to "Localized swelling / exophytic growth",
        "FIBROUS_BANDS" to "Palpable fibrous bands / reduced mouth opening",
        "PIGMENTATION" to "Altered mucosal pigmentation",
        "BLEEDING_INFLAMMATION" to "Mucosal inflammation / bleeding",
        "OTHER_CLINICAL_NOTE" to "Other clinical note (specify in remarks)",
    )

    val LESION_TYPES = listOf(
        "White Plaque / Patch",
        "Red Patch (Erythroplakia-like)",
        "Mixed Red & White Patch",
        "Non-Healing Ulcer",
        "Fibrous Bands / Stiff Mucosa",
        "Exophytic / Verrucous Growth",
        "Vesicle / Bulla",
        "Other Clinical Lesion",
    )

    val PERMANENT_UPPER_RIGHT = listOf("18", "17", "16", "15", "14", "13", "12", "11")
    val PERMANENT_UPPER_LEFT = listOf("21", "22", "23", "24", "25", "26", "27", "28")
    val PERMANENT_LOWER_RIGHT = listOf("48", "47", "46", "45", "44", "43", "42", "41")
    val PERMANENT_LOWER_LEFT = listOf("31", "32", "33", "34", "35", "36", "37", "38")

    val PRIMARY_UPPER_RIGHT = listOf("55", "54", "53", "52", "51")
    val PRIMARY_UPPER_LEFT = listOf("61", "62", "63", "64", "65")
    val PRIMARY_LOWER_RIGHT = listOf("85", "84", "83", "82", "81")
    val PRIMARY_LOWER_LEFT = listOf("71", "72", "73", "74", "75")

    val TOOTH_STATUSES = listOf("HEALTHY", "CARIES", "FILLED", "MISSING", "CROWN", "ROOT_STUMP", "FRACTURED", "MOBILE", "IMPACTED", "PROSTHESIS")
    val TOOTH_SURFACES = listOf("M" to "Mesial", "D" to "Distal", "O" to "Occlusal", "B" to "Buccal", "L" to "Lingual", "I" to "Incisal")

    val AI_REVIEW_STATUSES = listOf(
        "PENDING_REVIEW",
        "REVIEWED",
        "ACCEPTED",
        "REJECTED",
        "NEEDS_FURTHER_EXAMINATION",
    )
}
