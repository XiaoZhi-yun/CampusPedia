ALTER TABLE t_user DROP COLUMN real_name;
ALTER TABLE t_user DROP INDEX username;
ALTER TABLE t_user ADD UNIQUE KEY uk_student_no (student_no);
