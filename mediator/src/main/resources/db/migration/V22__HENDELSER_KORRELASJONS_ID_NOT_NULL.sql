UPDATE personregister_hendelse SET korrelasjons_id = gen_random_uuid() WHERE korrelasjons_id IS NULL;
UPDATE fremtidig_hendelse SET korrelasjons_id = gen_random_uuid() WHERE korrelasjons_id IS NULL;

ALTER TABLE personregister_hendelse
    ALTER COLUMN korrelasjons_id SET NOT NULL;
ALTER TABLE fremtidig_hendelse
    ALTER COLUMN korrelasjons_id SET NOT NULL;
