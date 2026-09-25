-- Structure of the legacy MySQL `spacz` database (mysqldump --no-data), used to test the migration.
SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS `amenity`;
CREATE TABLE `amenity` (
  `amenity_id` bigint NOT NULL AUTO_INCREMENT,
  `ac` bit(1) NOT NULL,
  `lockers` bit(1) NOT NULL,
  `newspapers` bit(1) NOT NULL,
  `water` bit(1) NOT NULL,
  `wifi` bit(1) NOT NULL,
  `block_id` bigint DEFAULT NULL,
  PRIMARY KEY (`amenity_id`),
  UNIQUE KEY `UKgk7fvde3gcfh0y52chnq9n3no` (`block_id`),
  CONSTRAINT `FKf4cwuk9b0svd2cjqiv8gspukr` FOREIGN KEY (`block_id`) REFERENCES `block` (`block_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `aspirant_user`;
CREATE TABLE `aspirant_user` (
  `aspirant_user_id` bigint NOT NULL AUTO_INCREMENT,
  `aadhar_number` varchar(255) DEFAULT NULL,
  `current_address` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `permanent_address` varchar(255) DEFAULT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`aspirant_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `block`;
CREATE TABLE `block` (
  `block_id` bigint NOT NULL AUTO_INCREMENT,
  `block_daily_price` double NOT NULL,
  `block_monthly_price` double NOT NULL,
  `block_name` varchar(255) DEFAULT NULL,
  `property_id` bigint DEFAULT NULL,
  PRIMARY KEY (`block_id`),
  KEY `FKp8auaxgh8q6ci4yqad3f6xs8u` (`property_id`),
  CONSTRAINT `FKp8auaxgh8q6ci4yqad3f6xs8u` FOREIGN KEY (`property_id`) REFERENCES `property` (`property_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `booking`;
CREATE TABLE `booking` (
  `booking_id` bigint NOT NULL AUTO_INCREMENT,
  `end_date` date DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `aspirant_user_id` bigint DEFAULT NULL,
  `seat_id` bigint DEFAULT NULL,
  PRIMARY KEY (`booking_id`),
  UNIQUE KEY `UKqooel92aivqm07s3md6vpbsj1` (`seat_id`),
  KEY `FKqam9b4tu2780iprka10850i9r` (`aspirant_user_id`),
  CONSTRAINT `FK7ryitbom1ln9okwlj2t9tt9ym` FOREIGN KEY (`seat_id`) REFERENCES `seat` (`seat_id`),
  CONSTRAINT `FKqam9b4tu2780iprka10850i9r` FOREIGN KEY (`aspirant_user_id`) REFERENCES `aspirant_user` (`aspirant_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `image`;
CREATE TABLE `image` (
  `image_id` bigint NOT NULL AUTO_INCREMENT,
  `image_url` varchar(255) DEFAULT NULL,
  `property_id` bigint DEFAULT NULL,
  PRIMARY KEY (`image_id`),
  KEY `FK6oqm8c6mg2dgedoppcbu8sm5q` (`property_id`),
  CONSTRAINT `FK6oqm8c6mg2dgedoppcbu8sm5q` FOREIGN KEY (`property_id`) REFERENCES `property` (`property_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `owner`;
CREATE TABLE `owner` (
  `owner_id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) DEFAULT NULL,
  `owner_email` varchar(255) DEFAULT NULL,
  `owner_name` varchar(255) DEFAULT NULL,
  `owner_phone_number` varchar(255) DEFAULT NULL,
  `login_id` bigint DEFAULT NULL,
  PRIMARY KEY (`owner_id`),
  UNIQUE KEY `UKtf1n9uerlejhx84i0q2d0nvuv` (`login_id`),
  CONSTRAINT `FKbobytlof83p3xgi4feh6c8d98` FOREIGN KEY (`login_id`) REFERENCES `user_login` (`login_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `property`;
CREATE TABLE `property` (
  `property_id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) DEFAULT NULL,
  `google_coordinates` varchar(255) DEFAULT NULL,
  `property_name` varchar(255) DEFAULT NULL,
  `owner_id` bigint DEFAULT NULL,
  PRIMARY KEY (`property_id`),
  KEY `FKj2cohq7sjhdetbls088cupcu3` (`owner_id`),
  CONSTRAINT `FKj2cohq7sjhdetbls088cupcu3` FOREIGN KEY (`owner_id`) REFERENCES `owner` (`owner_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `seat`;
CREATE TABLE `seat` (
  `seat_id` bigint NOT NULL AUTO_INCREMENT,
  `is_reserved` bit(1) NOT NULL,
  `seat_number` varchar(255) DEFAULT NULL,
  `seat_price` double NOT NULL,
  `block_id` bigint DEFAULT NULL,
  PRIMARY KEY (`seat_id`),
  KEY `FK59u09rx81komn3cyds8wekf8f` (`block_id`),
  CONSTRAINT `FK59u09rx81komn3cyds8wekf8f` FOREIGN KEY (`block_id`) REFERENCES `block` (`block_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `user_login`;
CREATE TABLE `user_login` (
  `login_id` bigint NOT NULL AUTO_INCREMENT,
  `is_owner_registered` bit(1) NOT NULL,
  `otp` varchar(255) DEFAULT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`login_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
SET FOREIGN_KEY_CHECKS=1;
