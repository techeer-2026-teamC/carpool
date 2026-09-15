CREATE TABLE meeting_attendance (
    post_id bigint NOT NULL REFERENCES posts(id),
    member_id bigint NOT NULL REFERENCES members(id),
    status varchar(20) NOT NULL CHECK (status IN ('MET','NO_SHOW')),
    updated_at timestamp NOT NULL,
    PRIMARY KEY (post_id, member_id)
);
