-- ORALCARE MySQL 8+ Complete Relational Schema

CREATE DATABASE IF NOT EXISTS oralcare_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE oralcare_db;



CREATE TABLE colleges (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	college_code VARCHAR(32) NOT NULL, 
	name VARCHAR(200) NOT NULL, 
	address TEXT, 
	city VARCHAR(100), 
	state VARCHAR(100), 
	pincode VARCHAR(20), 
	contact_email VARCHAR(150), 
	contact_phone VARCHAR(30), 
	is_active BOOL NOT NULL, 
	created_by_user_id INTEGER, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	is_deleted BOOL NOT NULL, 
	deleted_at DATETIME, 
	PRIMARY KEY (id)
);

CREATE TABLE centers (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	college_id INTEGER NOT NULL, 
	center_code VARCHAR(32) NOT NULL, 
	name VARCHAR(200) NOT NULL, 
	hub_name VARCHAR(150), 
	health_partner VARCHAR(150), 
	address TEXT, 
	city VARCHAR(100), 
	state VARCHAR(100), 
	contact_email VARCHAR(150), 
	contact_phone VARCHAR(30), 
	is_active BOOL NOT NULL, 
	created_by_user_id INTEGER, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	is_deleted BOOL NOT NULL, 
	deleted_at DATETIME, 
	PRIMARY KEY (id), 
	FOREIGN KEY(college_id) REFERENCES colleges (id) ON DELETE RESTRICT
);

CREATE TABLE users (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	user_uid VARCHAR(36) NOT NULL, 
	username VARCHAR(100) NOT NULL, 
	email VARCHAR(150), 
	full_name VARCHAR(150) NOT NULL, 
	phone VARCHAR(30), 
	password_hash VARCHAR(255) NOT NULL, 
	`role` ENUM('SUPER_ADMIN','COLLEGE_ADMIN','CENTER_ADMIN','HEALTH_ORGANIZER') NOT NULL, 
	college_id INTEGER, 
	center_id INTEGER, 
	created_by_user_id INTEGER, 
	is_active BOOL NOT NULL, 
	must_change_password BOOL NOT NULL, 
	failed_login_attempts INTEGER NOT NULL, 
	locked_until DATETIME, 
	last_login_at DATETIME, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	is_deleted BOOL NOT NULL, 
	deleted_at DATETIME, 
	PRIMARY KEY (id), 
	FOREIGN KEY(college_id) REFERENCES colleges (id) ON DELETE RESTRICT, 
	FOREIGN KEY(center_id) REFERENCES centers (id) ON DELETE RESTRICT, 
	FOREIGN KEY(created_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE activity_logs (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	user_id INTEGER, 
	`role` VARCHAR(40) NOT NULL, 
	college_id INTEGER, 
	center_id INTEGER, 
	action VARCHAR(80) NOT NULL, 
	entity_type VARCHAR(80) NOT NULL, 
	entity_id VARCHAR(80), 
	ip_address VARCHAR(64), 
	details JSON, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE app_settings (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	setting_key VARCHAR(100) NOT NULL, 
	setting_value TEXT NOT NULL, 
	description VARCHAR(255), 
	updated_by_user_id INTEGER, 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	FOREIGN KEY(updated_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE awareness_content (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	title VARCHAR(200) NOT NULL, 
	summary VARCHAR(500), 
	body TEXT NOT NULL, 
	category VARCHAR(80) NOT NULL, 
	language VARCHAR(30) NOT NULL, 
	content_type ENUM('ARTICLE','POSTER','INFOGRAPHIC') NOT NULL, 
	poster_storage_path VARCHAR(500), 
	poster_file_name VARCHAR(255), 
	college_id INTEGER, 
	is_published BOOL NOT NULL, 
	created_by_user_id INTEGER NOT NULL, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	is_deleted BOOL NOT NULL, 
	deleted_at DATETIME, 
	PRIMARY KEY (id), 
	FOREIGN KEY(college_id) REFERENCES colleges (id) ON DELETE SET NULL, 
	FOREIGN KEY(created_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE educational_resources (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	title VARCHAR(200) NOT NULL, 
	description TEXT, 
	category VARCHAR(80) NOT NULL, 
	language VARCHAR(30) NOT NULL, 
	resource_type ENUM('MANUAL_PDF','YOUTUBE_VIDEO','DOCUMENT','IMAGE') NOT NULL, 
	external_url VARCHAR(500), 
	file_storage_path VARCHAR(500), 
	file_name VARCHAR(255), 
	mime_type VARCHAR(80), 
	college_id INTEGER, 
	is_published BOOL NOT NULL, 
	created_by_user_id INTEGER NOT NULL, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	is_deleted BOOL NOT NULL, 
	deleted_at DATETIME, 
	PRIMARY KEY (id), 
	FOREIGN KEY(college_id) REFERENCES colleges (id) ON DELETE SET NULL, 
	FOREIGN KEY(created_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE lab_reference_ranges (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	parameter_key VARCHAR(60) NOT NULL, 
	display_name VARCHAR(120) NOT NULL, 
	unit VARCHAR(40) NOT NULL, 
	min_value FLOAT, 
	max_value FLOAT, 
	standard_source VARCHAR(200), 
	updated_by_user_id INTEGER, 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	FOREIGN KEY(updated_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE organizers (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	user_id INTEGER NOT NULL, 
	college_id INTEGER NOT NULL, 
	center_id INTEGER NOT NULL, 
	employee_code VARCHAR(50) NOT NULL, 
	designation VARCHAR(120) NOT NULL, 
	qualification VARCHAR(150), 
	assigned_area VARCHAR(200), 
	is_active BOOL NOT NULL, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	is_deleted BOOL NOT NULL, 
	deleted_at DATETIME, 
	PRIMARY KEY (id), 
	FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE, 
	FOREIGN KEY(college_id) REFERENCES colleges (id) ON DELETE RESTRICT, 
	FOREIGN KEY(center_id) REFERENCES centers (id) ON DELETE RESTRICT
);

CREATE TABLE password_reset_tokens (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	user_id INTEGER NOT NULL, 
	token_hash VARCHAR(128) NOT NULL, 
	expires_at DATETIME NOT NULL, 
	is_used BOOL NOT NULL, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE patients (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	patient_uid VARCHAR(40) NOT NULL, 
	idempotency_key VARCHAR(100), 
	college_id INTEGER NOT NULL, 
	center_id INTEGER NOT NULL, 
	assigned_organizer_id INTEGER, 
	created_by_user_id INTEGER NOT NULL, 
	registration_date DATE NOT NULL, 
	prefix VARCHAR(20), 
	first_name VARCHAR(100) NOT NULL, 
	last_name VARCHAR(100) NOT NULL, 
	age INTEGER NOT NULL, 
	sex ENUM('MALE','FEMALE','OTHER') NOT NULL, 
	religion VARCHAR(80), 
	marital_status VARCHAR(50), 
	education VARCHAR(120), 
	occupation VARCHAR(120), 
	family_income_per_month VARCHAR(80), 
	phone_number VARCHAR(25) NOT NULL, 
	address TEXT NOT NULL, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	is_deleted BOOL NOT NULL, 
	deleted_at DATETIME, 
	PRIMARY KEY (id), 
	FOREIGN KEY(college_id) REFERENCES colleges (id) ON DELETE RESTRICT, 
	FOREIGN KEY(center_id) REFERENCES centers (id) ON DELETE RESTRICT, 
	FOREIGN KEY(assigned_organizer_id) REFERENCES users (id) ON DELETE SET NULL, 
	FOREIGN KEY(created_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE refresh_tokens (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	user_id INTEGER NOT NULL, 
	token_hash VARCHAR(128) NOT NULL, 
	token_family VARCHAR(64) NOT NULL, 
	is_revoked BOOL NOT NULL, 
	expires_at DATETIME NOT NULL, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	revoked_at DATETIME, 
	PRIMARY KEY (id), 
	FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE feedback (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	college_id INTEGER, 
	center_id INTEGER, 
	patient_id INTEGER, 
	created_by_user_id INTEGER NOT NULL, 
	patient_name VARCHAR(150) NOT NULL, 
	email VARCHAR(150) NOT NULL, 
	rating INTEGER NOT NULL, 
	message TEXT NOT NULL, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	is_deleted BOOL NOT NULL, 
	deleted_at DATETIME, 
	PRIMARY KEY (id), 
	FOREIGN KEY(college_id) REFERENCES colleges (id) ON DELETE RESTRICT, 
	FOREIGN KEY(center_id) REFERENCES centers (id) ON DELETE RESTRICT, 
	FOREIGN KEY(patient_id) REFERENCES patients (id) ON DELETE SET NULL, 
	FOREIGN KEY(created_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE screening_records (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	screening_uid VARCHAR(40) NOT NULL, 
	idempotency_key VARCHAR(100), 
	patient_id INTEGER NOT NULL, 
	college_id INTEGER NOT NULL, 
	center_id INTEGER NOT NULL, 
	organizer_user_id INTEGER NOT NULL, 
	screening_date DATE NOT NULL, 
	workflow_status ENUM('DRAFT','SUBMITTED','REVIEWED') NOT NULL, 
	current_step INTEGER NOT NULL, 
	outcome ENUM('NORMAL','ABNORMAL','PENDING_CLINICAL_REVIEW') NOT NULL, 
	lesion_present BOOL NOT NULL, 
	clinician_review_status VARCHAR(40) NOT NULL, 
	reviewed_by_user_id INTEGER, 
	reviewed_at DATETIME, 
	submitted_at DATETIME, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	is_deleted BOOL NOT NULL, 
	deleted_at DATETIME, 
	PRIMARY KEY (id), 
	FOREIGN KEY(patient_id) REFERENCES patients (id) ON DELETE CASCADE, 
	FOREIGN KEY(college_id) REFERENCES colleges (id) ON DELETE RESTRICT, 
	FOREIGN KEY(center_id) REFERENCES centers (id) ON DELETE RESTRICT, 
	FOREIGN KEY(organizer_user_id) REFERENCES users (id) ON DELETE RESTRICT, 
	FOREIGN KEY(reviewed_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE clinical_findings (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	screening_id INTEGER NOT NULL, 
	anatomical_region VARCHAR(100) NOT NULL, 
	sub_region VARCHAR(100), 
	status ENUM('NOT_EXAMINED','NORMAL','ABNORMAL') NOT NULL, 
	remarks TEXT, 
	reference_image_key VARCHAR(120), 
	PRIMARY KEY (id), 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE
);

CREATE TABLE consent_records (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	screening_id INTEGER NOT NULL, 
	patient_id INTEGER NOT NULL, 
	hub VARCHAR(150) NOT NULL, 
	health_partner VARCHAR(150) NOT NULL, 
	healthworker_name VARCHAR(150) NOT NULL, 
	participant_info_sheet_ack BOOL NOT NULL, 
	consent_form_signed BOOL NOT NULL, 
	consent_accepted BOOL NOT NULL, 
	terms_accepted BOOL NOT NULL, 
	geolocation_label VARCHAR(255), 
	latitude FLOAT, 
	longitude FLOAT, 
	location_permission_granted BOOL NOT NULL, 
	recorded_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	UNIQUE (screening_id), 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE, 
	FOREIGN KEY(patient_id) REFERENCES patients (id) ON DELETE CASCADE
);

CREATE TABLE consultation_summaries (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	screening_id INTEGER NOT NULL, 
	outcome ENUM('NORMAL','ABNORMAL','PENDING_CLINICAL_REVIEW') NOT NULL, 
	outcome_reason TEXT NOT NULL, 
	clinical_recommendations TEXT, 
	summary_payload JSON NOT NULL, 
	generated_by_user_id INTEGER NOT NULL, 
	generated_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	UNIQUE (screening_id), 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE, 
	FOREIGN KEY(generated_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE dental_histories (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	screening_id INTEGER NOT NULL, 
	cleaning_aid VARCHAR(80) NOT NULL, 
	material_used VARCHAR(80) NOT NULL, 
	material_other VARCHAR(120), 
	brushing_method VARCHAR(80), 
	brushing_frequency VARCHAR(80), 
	brushing_duration VARCHAR(80), 
	toothbrush_change_frequency VARCHAR(100), 
	other_hygiene_aids VARCHAR(150), 
	past_dental_history BOOL NOT NULL, 
	past_dental_details TEXT, 
	denture_usage BOOL NOT NULL, 
	denture_details TEXT, 
	PRIMARY KEY (id), 
	UNIQUE (screening_id), 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE
);

CREATE TABLE follow_up_records (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	patient_id INTEGER NOT NULL, 
	screening_id INTEGER NOT NULL, 
	scheduled_date DATE NOT NULL, 
	status ENUM('SCHEDULED','COMPLETED','MISSED','CANCELLED') NOT NULL, 
	notes TEXT, 
	created_by_user_id INTEGER NOT NULL, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	FOREIGN KEY(patient_id) REFERENCES patients (id) ON DELETE CASCADE, 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE, 
	FOREIGN KEY(created_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE habit_histories (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	screening_id INTEGER NOT NULL, 
	habit_category VARCHAR(50) NOT NULL, 
	habit_type VARCHAR(120), 
	frequency VARCHAR(100), 
	duration VARCHAR(100), 
	quantity VARCHAR(100), 
	usage_status VARCHAR(40) NOT NULL, 
	remarks TEXT, 
	PRIMARY KEY (id), 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE
);

CREATE TABLE lab_investigations (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	screening_id INTEGER NOT NULL, 
	test_date DATE, 
	haemoglobin FLOAT, 
	haemoglobin_unit VARCHAR(30), 
	rbc FLOAT, 
	rbc_unit VARCHAR(30), 
	total_count FLOAT, 
	total_count_unit VARCHAR(30), 
	platelet_count FLOAT, 
	platelet_count_unit VARCHAR(30), 
	percentage FLOAT, 
	percentage_label VARCHAR(80), 
	biopsy_report_summary TEXT, 
	biopsy_attachment_path VARCHAR(500), 
	biopsy_attachment_name VARCHAR(255), 
	reference_ranges_snapshot JSON, 
	laboratory_remarks TEXT, 
	PRIMARY KEY (id), 
	UNIQUE (screening_id), 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE
);

CREATE TABLE lesion_details (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	screening_id INTEGER NOT NULL, 
	anatomical_location VARCHAR(150) NOT NULL, 
	lesion_type VARCHAR(150) NOT NULL, 
	size VARCHAR(80), 
	appearance VARCHAR(200), 
	duration VARCHAR(100), 
	symptoms TEXT, 
	clinical_observations TEXT, 
	remarks TEXT, 
	referral_recommendation VARCHAR(255), 
	PRIMARY KEY (id), 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE
);

CREATE TABLE medical_histories (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	screening_id INTEGER NOT NULL, 
	family_history_conditions JSON NOT NULL, 
	family_history_other TEXT, 
	past_medical_conditions JSON NOT NULL, 
	past_medical_other TEXT, 
	current_medications TEXT, 
	allergies TEXT, 
	PRIMARY KEY (id), 
	UNIQUE (screening_id), 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE
);

CREATE TABLE patient_images (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	image_uid VARCHAR(40) NOT NULL, 
	patient_id INTEGER NOT NULL, 
	screening_id INTEGER NOT NULL, 
	clinical_region VARCHAR(120) NOT NULL, 
	sub_region VARCHAR(120), 
	storage_path VARCHAR(500) NOT NULL, 
	original_filename VARCHAR(255) NOT NULL, 
	mime_type VARCHAR(80) NOT NULL, 
	file_size_bytes INTEGER NOT NULL, 
	width_px INTEGER, 
	height_px INTEGER, 
	capture_date DATETIME NOT NULL DEFAULT now(), 
	uploaded_by_user_id INTEGER NOT NULL, 
	image_metadata JSON, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	FOREIGN KEY(patient_id) REFERENCES patients (id) ON DELETE CASCADE, 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE, 
	FOREIGN KEY(uploaded_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE personal_histories (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	screening_id INTEGER NOT NULL, 
	diet_history VARCHAR(100) NOT NULL, 
	spice_consumption VARCHAR(100) NOT NULL, 
	has_habit_history BOOL NOT NULL, 
	notes TEXT, 
	updated_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	UNIQUE (screening_id), 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE
);

CREATE TABLE referral_records (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	patient_id INTEGER NOT NULL, 
	screening_id INTEGER NOT NULL, 
	referred_to VARCHAR(200) NOT NULL, 
	department VARCHAR(120), 
	urgency ENUM('ROUTINE','PRIORITY','URGENT') NOT NULL, 
	reason TEXT NOT NULL, 
	status VARCHAR(40) NOT NULL, 
	created_by_user_id INTEGER NOT NULL, 
	created_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	FOREIGN KEY(patient_id) REFERENCES patients (id) ON DELETE CASCADE, 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE, 
	FOREIGN KEY(created_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE teeth_examinations (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	screening_id INTEGER NOT NULL, 
	dentition_type ENUM('PERMANENT','PRIMARY') NOT NULL, 
	tooth_number VARCHAR(10) NOT NULL, 
	status VARCHAR(60) NOT NULL, 
	clinical_finding VARCHAR(200), 
	remarks TEXT, 
	PRIMARY KEY (id), 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE
);

CREATE TABLE ai_analysis (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	patient_image_id INTEGER NOT NULL, 
	screening_id INTEGER NOT NULL, 
	status ENUM('PENDING','COMPLETED','MODEL_UNAVAILABLE','FAILED') NOT NULL, 
	model_name VARCHAR(120) NOT NULL, 
	model_version VARCHAR(60) NOT NULL, 
	image_quality_status ENUM('ACCEPTABLE','LOW_RESOLUTION','BLURRY','POOR_LIGHTING','INVALID') NOT NULL, 
	quality_metrics JSON, 
	model_output JSON, 
	confidence_score FLOAT, 
	review_status ENUM('PENDING_REVIEW','REVIEWED','ACCEPTED','REJECTED','NEEDS_FURTHER_EXAMINATION') NOT NULL, 
	assistive_disclaimer VARCHAR(300) NOT NULL, 
	analyzed_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	FOREIGN KEY(patient_image_id) REFERENCES patient_images (id) ON DELETE CASCADE, 
	FOREIGN KEY(screening_id) REFERENCES screening_records (id) ON DELETE CASCADE
);

CREATE TABLE clinical_observations (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	clinical_finding_id INTEGER NOT NULL, 
	observation_code VARCHAR(80) NOT NULL, 
	observation_label VARCHAR(200) NOT NULL, 
	details TEXT, 
	PRIMARY KEY (id), 
	FOREIGN KEY(clinical_finding_id) REFERENCES clinical_findings (id) ON DELETE CASCADE
);

CREATE TABLE tooth_surfaces (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	teeth_examination_id INTEGER NOT NULL, 
	surface_code VARCHAR(20) NOT NULL, 
	`condition` VARCHAR(100) NOT NULL, 
	notes TEXT, 
	PRIMARY KEY (id), 
	FOREIGN KEY(teeth_examination_id) REFERENCES teeth_examinations (id) ON DELETE CASCADE
);

CREATE TABLE ai_review_records (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	ai_analysis_id INTEGER NOT NULL, 
	reviewer_user_id INTEGER NOT NULL, 
	review_status ENUM('PENDING_REVIEW','REVIEWED','ACCEPTED','REJECTED','NEEDS_FURTHER_EXAMINATION') NOT NULL, 
	clinician_notes TEXT, 
	corrected_findings JSON, 
	reviewed_at DATETIME NOT NULL DEFAULT now(), 
	PRIMARY KEY (id), 
	FOREIGN KEY(ai_analysis_id) REFERENCES ai_analysis (id) ON DELETE CASCADE, 
	FOREIGN KEY(reviewer_user_id) REFERENCES users (id) ON DELETE RESTRICT
);