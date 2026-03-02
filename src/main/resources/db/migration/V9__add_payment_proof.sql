ALTER TABLE deals
  ADD COLUMN payment_proof_amount DECIMAL(19,2) NULL,
  ADD COLUMN payment_proof_uploaded_at DATETIME(6) NULL,
  ADD COLUMN payment_proof LONGBLOB NULL,
  ADD COLUMN payment_proof_content_type VARCHAR(255) NULL;
