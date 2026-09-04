ALTER TABLE tb_credentials
    ALTER COLUMN vc_payload TYPE TEXT USING vc_payload::text;
