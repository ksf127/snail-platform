-- 流水线增加「端」维度：前端 / 后端
ALTER TABLE pipeline ADD COLUMN side VARCHAR(16) NOT NULL DEFAULT 'BACKEND' AFTER executor_type;

UPDATE pipeline
SET side = 'FRONTEND'
WHERE definition_json LIKE '%node:%'
   OR definition_json LIKE '%npm %'
   OR definition_json LIKE '%yarn %'
   OR definition_json LIKE '%pnpm %'
   OR definition_json LIKE '%vite%';
