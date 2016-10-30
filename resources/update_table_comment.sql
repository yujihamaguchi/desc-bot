UPDATE table_comment SET table_comment = ?, user_name = ?
WHERE
  CASE WHEN instance_name IS NULL THEN ? ELSE instance_name END = ?
	AND CASE WHEN table_schema IS NULL THEN ? ELSE table_schema END = ?
	AND table_name = ?
