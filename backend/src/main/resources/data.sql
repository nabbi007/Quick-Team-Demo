-- Seed data for QuickPoll
-- Passwords are BCrypt hash of password123
INSERT INTO users (id,email,password,full_name,role,created_at) VALUES
(1,'admin@quickpoll.com','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','Admin User','ADMIN',NOW()),
(2,'user@quickpoll.com','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','Regular User','USER',NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO polls (id,title,description,creator_id,multi_select,expires_at,active,created_at) VALUES
(1,'Best Programming Language','Vote for your favorite',1,false,NOW()+INTERVAL '30 days',true,NOW()),
(2,'Preferred Work Model','What arrangement do you prefer?',1,false,NOW()+INTERVAL '14 days',true,NOW()),
(3,'Favorite Frontend Framework','Which do you use most?',2,false,NOW()+INTERVAL '7 days',true,NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO poll_options (id,poll_id,option_text,vote_count) VALUES
(1,1,'Java',15),(2,1,'Python',22),(3,1,'JavaScript',18),(4,1,'Go',8),
(5,2,'Remote',30),(6,2,'Hybrid',25),(7,2,'On-site',10),
(8,3,'Angular',20),(9,3,'React',28),(10,3,'Vue',15),(11,3,'Svelte',7)
ON CONFLICT (id) DO NOTHING;

INSERT INTO votes (id,poll_id,option_id,user_id,created_at) VALUES
(1,1,1,1,NOW()),(2,1,2,2,NOW()),(3,2,5,1,NOW()),(4,2,6,2,NOW()),(5,3,8,1,NOW())
ON CONFLICT DO NOTHING;
