UPDATE authentications
SET status = 'DECLINED'
WHERE status = 'REVIEW';
