"""Approved clinical taxonomy and configurable standards for OralCare."""

ANATOMICAL_REGIONS = [
    {
        "code": "LABIAL_MUCOSA",
        "name": "Labial Mucosa",
        "sub_regions": ["Upper", "Lower"],
        "reference_image_key": "ref_labial_mucosa",
    },
    {
        "code": "BUCCAL_MUCOSA",
        "name": "Buccal Mucosa",
        "sub_regions": ["Right", "Left"],
        "reference_image_key": "ref_buccal_mucosa",
    },
    {
        "code": "PALATAL_MUCOSA",
        "name": "Palatal Mucosa",
        "sub_regions": ["Hard Palate", "Soft Palate"],
        "reference_image_key": "ref_palatal_mucosa",
    },
    {
        "code": "ALVEOLAR_MUCOSA",
        "name": "Alveolar Mucosa",
        "sub_regions": ["Right", "Left"],
        "reference_image_key": "ref_alveolar_mucosa",
    },
    {
        "code": "TONGUE",
        "name": "Tongue",
        "sub_regions": ["Dorsal", "Lateral", "Ventral", "Anterior", "Posterior", "Right", "Left"],
        "reference_image_key": "ref_tongue",
    },
    {
        "code": "FLOOR_OF_MOUTH",
        "name": "Floor of the Mouth",
        "sub_regions": ["General"],
        "reference_image_key": "ref_floor_of_mouth",
    },
    {
        "code": "PALATINE_TONSIL",
        "name": "Palatine Tonsil",
        "sub_regions": ["Right", "Left"],
        "reference_image_key": "ref_palatine_tonsil",
    },
    {
        "code": "PILLAR_OF_FAUCES",
        "name": "Pillar of Fauces",
        "sub_regions": ["Anterior", "Posterior"],
        "reference_image_key": "ref_pillar_of_fauces",
    },
    {
        "code": "FRENIAL_ATTACHMENT",
        "name": "Frenial Attachment",
        "sub_regions": ["Maxillary", "Lingual", "Lower"],
        "reference_image_key": "ref_frenial_attachment",
    },
    {
        "code": "ANGLE_OF_MOUTH",
        "name": "Angle of the Mouth",
        "sub_regions": ["Right", "Left"],
        "reference_image_key": "ref_angle_of_mouth",
    },
    {
        "code": "LIP_VERMILLION_BORDER",
        "name": "Lip and Vermillion Border",
        "sub_regions": ["Upper", "Lower"],
        "reference_image_key": "ref_lip_vermillion",
    },
]

APPROVED_CLINICAL_OBSERVATIONS = [
    {"code": "WHITE_PATCH", "label": "White patch / plaque observation"},
    {"code": "RED_PATCH", "label": "Red / erythematous patch observation"},
    {"code": "MIXED_RED_WHITE", "label": "Mixed red-and-white mucosal change"},
    {"code": "ULCERATION", "label": "Ulceration / mucosal erosion"},
    {"code": "SWELLING_GROWTH", "label": "Localized swelling / exophytic growth"},
    {"code": "FIBROUS_BANDS", "label": "Palpable fibrous bands / reduced mouth opening"},
    {"code": "PIGMENTATION", "label": "Altered mucosal pigmentation"},
    {"code": "BLEEDING_INFLAMMATION", "label": "Mucosal inflammation / bleeding"},
    {"code": "OTHER_CLINICAL_NOTE", "label": "Other clinician observation (specify in remarks)"},
]

FDI_PERMANENT_TEETH = {
    "upper_right": ["18", "17", "16", "15", "14", "13", "12", "11"],
    "upper_left": ["21", "22", "23", "24", "25", "26", "27", "28"],
    "lower_right": ["48", "47", "46", "45", "44", "43", "42", "41"],
    "lower_left": ["31", "32", "33", "34", "35", "36", "37", "38"],
}

FDI_PRIMARY_TEETH = {
    "upper_right": ["55", "54", "53", "52", "51"],
    "upper_left": ["61", "62", "63", "64", "65"],
    "lower_right": ["85", "84", "83", "82", "81"],
    "lower_left": ["71", "72", "73", "74", "75"],
}
