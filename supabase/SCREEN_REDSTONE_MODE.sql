-- Run before deploying the updated mpsq-api function.
BEGIN;
ALTER TABLE public.mpsq_screens DROP CONSTRAINT IF EXISTS mpsq_screens_mode_check;
ALTER TABLE public.mpsq_screens ADD CONSTRAINT mpsq_screens_mode_check
  CHECK (mode IN ('KINO', 'CAMERA', 'REDSTONE', 'MPSQ_REDSTONE'));
COMMIT;
-- Existing KINO rows are deliberately not bulk-converted: their original intent is unknown.
