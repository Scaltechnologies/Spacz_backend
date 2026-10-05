-- Vendors registered through the Partner app have no "submit profile" step, so vendors whose halls
-- were already submitted stayed DRAFT and could never be approved (their halls never went live).
-- Hall submission now submits a DRAFT vendor too; apply the same rule to the existing ones.
-- Status transition only (DRAFT -> PENDING, awaiting admin approval); no rows are deleted.
UPDATE vendor_profiles v
SET status       = 'PENDING',
    submitted_at = COALESCE(v.submitted_at, now()),
    updated_at   = now(),
    version      = COALESCE(v.version, 0) + 1
WHERE v.status = 'DRAFT'
  AND EXISTS (SELECT 1 FROM study_halls h
              WHERE h.vendor_profile_id = v.id AND h.status = 'PENDING_APPROVAL');
