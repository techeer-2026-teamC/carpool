CREATE EXTENSION IF NOT EXISTS postgis;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS origin_geography geography(Point,4326)
    GENERATED ALWAYS AS (CASE WHEN departure_lat BETWEEN -90 AND 90 AND departure_lng BETWEEN -180 AND 180
        THEN ST_SetSRID(ST_MakePoint(departure_lng, departure_lat),4326)::geography END) STORED;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS destination_geography geography(Point,4326)
    GENERATED ALWAYS AS (CASE WHEN destination_lat BETWEEN -90 AND 90 AND destination_lng BETWEEN -180 AND 180
        THEN ST_SetSRID(ST_MakePoint(destination_lng, destination_lat),4326)::geography END) STORED;
CREATE INDEX IF NOT EXISTS idx_posts_origin_geography ON posts USING gist(origin_geography);
CREATE INDEX IF NOT EXISTS idx_posts_destination_geography ON posts USING gist(destination_geography);
CREATE INDEX IF NOT EXISTS idx_posts_discovery ON posts(type, status, departure_time, id) WHERE deleted = false;
