-- MySQL dump 10.13  Distrib 8.0.18, for Linux (x86_64)
--
-- Host: localhost    Database: aiai
-- ------------------------------------------------------
-- Server version	8.0.18

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `mh_account`
--

DROP TABLE IF EXISTS `mh_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_account` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `COMPANY_ID` int(10) unsigned NOT NULL,
  `USERNAME` varchar(30) NOT NULL,
  `PASSWORD` varchar(100) NOT NULL,
  `ROLES` varchar(100) DEFAULT NULL,
  `PUBLIC_NAME` varchar(100) DEFAULT NULL,
  `is_acc_not_expired` tinyint(1) NOT NULL DEFAULT '1',
  `is_not_locked` tinyint(1) NOT NULL DEFAULT '0',
  `is_cred_not_expired` tinyint(1) NOT NULL DEFAULT '0',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `mail_address` varchar(100) DEFAULT NULL,
  `PHONE` varchar(100) DEFAULT NULL,
  `PHONE_AS_STR` varchar(100) DEFAULT NULL,
  `CREATED_ON` bigint(20) NOT NULL,
  `UPDATED_ON` bigint(20) NOT NULL,
  `SECRET_KEY` varchar(25) DEFAULT NULL,
  `TWO_FA` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `mh_account_username_unq_idx` (`USERNAME`),
  KEY `mh_account_company_id_idx` (`COMPANY_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_account`
--

LOCK TABLES `mh_account` WRITE;
/*!40000 ALTER TABLE `mh_account` DISABLE KEYS */;
INSERT INTO `mh_account` VALUES (1,2,2,'aiai','$2a$10$wVuFdkWRyLd0yCz0lD5KoOQxLUJDSAQ3qfdayvF9VZUJjC6ACG0qu','ROLE_ADMIN','aiai, Admin',1,1,1,1,NULL,NULL,NULL,1583218639165,1583218674213,NULL,0),(2,0,2,'rest_user','$2a$10$OjMWt5holzuzURUOLI.nquUUgUNH72Mfg0ZPTOTfAOMIzvrugAH6O','ROLE_SERVER_REST_ACCESS','rest_user',1,1,1,1,NULL,NULL,NULL,1583218882270,1583218882270,NULL,0);
/*!40000 ALTER TABLE `mh_account` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_atlas`
--

DROP TABLE IF EXISTS `mh_atlas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_atlas` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `COMPANY_ID` int(10) unsigned NOT NULL,
  `NAME` varchar(50) NOT NULL,
  `DESCRIPTION` varchar(250) NOT NULL,
  `CODE` varchar(50) NOT NULL,
  `CREATED_ON` bigint(20) NOT NULL,
  `EXPERIMENT` longtext NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_atlas`
--

LOCK TABLES `mh_atlas` WRITE;
/*!40000 ALTER TABLE `mh_atlas` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_atlas` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_atlas_task`
--

DROP TABLE IF EXISTS `mh_atlas_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_atlas_task` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `ATLAS_ID` decimal(10,0) NOT NULL,
  `TASK_ID` decimal(10,0) NOT NULL,
  `PARAMS` mediumtext NOT NULL,
  PRIMARY KEY (`ID`),
  KEY `mh_atlas_task_atlas_id_idx` (`ATLAS_ID`),
  KEY `mh_atlas_task_atlas_id_task_id_idx` (`ATLAS_ID`,`TASK_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_atlas_task`
--

LOCK TABLES `mh_atlas_task` WRITE;
/*!40000 ALTER TABLE `mh_atlas_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_atlas_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_batch`
--

DROP TABLE IF EXISTS `mh_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_batch` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `COMPANY_ID` int(10) unsigned NOT NULL,
  `ACCOUNT_ID` int(10) unsigned DEFAULT NULL,
  `PLAN_ID` decimal(10,0) NOT NULL,
  `DATA_ID` decimal(10,0) DEFAULT NULL,
  `CREATED_ON` bigint(20) NOT NULL,
  `EXEC_STATE` tinyint(1) NOT NULL DEFAULT '0',
  `PARAMS` mediumtext,
  `IS_DELETED` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_batch`
--

LOCK TABLES `mh_batch` WRITE;
/*!40000 ALTER TABLE `mh_batch` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_batch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_batch_workbook`
--

DROP TABLE IF EXISTS `mh_batch_workbook`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_batch_workbook` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `BATCH_ID` decimal(10,0) NOT NULL,
  `WORKBOOK_ID` decimal(10,0) NOT NULL,
  PRIMARY KEY (`ID`),
  KEY `mh_batch_workbook_batch_id_idx` (`BATCH_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_batch_workbook`
--

LOCK TABLES `mh_batch_workbook` WRITE;
/*!40000 ALTER TABLE `mh_batch_workbook` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_batch_workbook` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_company`
--

DROP TABLE IF EXISTS `mh_company`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_company` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `UNIQUE_ID` int(10) unsigned NOT NULL,
  `NAME` varchar(50) NOT NULL,
  `PARAMS` mediumtext,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `mh_company_unique_id_unq_idx` (`UNIQUE_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_company`
--

LOCK TABLES `mh_company` WRITE;
/*!40000 ALTER TABLE `mh_company` DISABLE KEYS */;
INSERT INTO `mh_company` VALUES (1,0,1,'master company',''),(2,0,2,'Demo','createdOn: 1583218337396\nupdatedOn: 1583218337396\nversion: 2\n');
/*!40000 ALTER TABLE `mh_company` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_data`
--

DROP TABLE IF EXISTS `mh_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_data` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `CODE` varchar(200) NOT NULL,
  `POOL_CODE` varchar(250) NOT NULL,
  `DATA_TYPE` decimal(2,0) NOT NULL,
  `REF_ID` decimal(10,0) DEFAULT NULL,
  `REF_TYPE` varchar(15) DEFAULT NULL,
  `UPLOAD_TS` timestamp NOT NULL ON UPDATE CURRENT_TIMESTAMP,
  `DATA` longblob,
  `CHECKSUM` varchar(2048) DEFAULT NULL,
  `IS_VALID` tinyint(1) NOT NULL DEFAULT '0',
  `IS_MANUAL` tinyint(1) NOT NULL DEFAULT '0',
  `FILENAME` varchar(150) DEFAULT NULL,
  `PARAMS` mediumtext NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `mh_data_code_unq_idx` (`CODE`),
  KEY `mh_data_data_type_idx` (`DATA_TYPE`),
  KEY `mh_data_ref_id_ref_type_idx` (`REF_ID`,`REF_TYPE`),
  KEY `mh_data_ref_type_idx` (`REF_TYPE`),
  KEY `mh_data_pool_code_id_idx` (`POOL_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_data`
--

LOCK TABLES `mh_data` WRITE;
/*!40000 ALTER TABLE `mh_data` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_event`
--

DROP TABLE IF EXISTS `mh_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_event` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `COMPANY_ID` int(10) unsigned DEFAULT NULL,
  `CREATED_ON` bigint(20) unsigned NOT NULL,
  `PERIOD` int(10) unsigned NOT NULL,
  `EVENT` varchar(50) NOT NULL,
  `PARAMS` mediumtext NOT NULL,
  PRIMARY KEY (`ID`),
  KEY `mh_event_period_idx` (`PERIOD`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_event`
--

LOCK TABLES `mh_event` WRITE;
/*!40000 ALTER TABLE `mh_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_experiment`
--

DROP TABLE IF EXISTS `mh_experiment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_experiment` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `WORKBOOK_ID` decimal(10,0) DEFAULT NULL,
  `CODE` varchar(50) NOT NULL,
  `PARAMS` mediumtext NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `mh_experiment_code_unq_idx` (`CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_experiment`
--

LOCK TABLES `mh_experiment` WRITE;
/*!40000 ALTER TABLE `mh_experiment` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_experiment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_gen_ids`
--

DROP TABLE IF EXISTS `mh_gen_ids`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_gen_ids` (
  `SEQUENCE_NAME` varchar(50) NOT NULL,
  `SEQUENCE_NEXT_VALUE` decimal(10,0) NOT NULL,
  UNIQUE KEY `mh_gen_ids_sequence_name_unq_idx` (`SEQUENCE_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_gen_ids`
--

LOCK TABLES `mh_gen_ids` WRITE;
/*!40000 ALTER TABLE `mh_gen_ids` DISABLE KEYS */;
INSERT INTO `mh_gen_ids` VALUES ('mh_ids',2);
/*!40000 ALTER TABLE `mh_gen_ids` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_ids`
--

DROP TABLE IF EXISTS `mh_ids`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_ids` (
  `ID` int(10) unsigned NOT NULL,
  `STUB` varchar(1) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_ids`
--

LOCK TABLES `mh_ids` WRITE;
/*!40000 ALTER TABLE `mh_ids` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_ids` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_launchpad_address`
--

DROP TABLE IF EXISTS `mh_launchpad_address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_launchpad_address` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `URL` varchar(200) NOT NULL,
  `DESCRIPTION` varchar(100) NOT NULL,
  `SIGNATURE` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_launchpad_address`
--

LOCK TABLES `mh_launchpad_address` WRITE;
/*!40000 ALTER TABLE `mh_launchpad_address` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_launchpad_address` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_log_data`
--

DROP TABLE IF EXISTS `mh_log_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_log_data` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `REF_ID` decimal(10,0) NOT NULL,
  `VERSION` decimal(5,0) NOT NULL,
  `UPDATE_TS` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `LOG_TYPE` decimal(5,0) NOT NULL,
  `LOG_DATA` mediumtext NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_log_data`
--

LOCK TABLES `mh_log_data` WRITE;
/*!40000 ALTER TABLE `mh_log_data` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_log_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_plan`
--

DROP TABLE IF EXISTS `mh_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_plan` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `COMPANY_ID` int(10) unsigned NOT NULL,
  `CODE` varchar(50) NOT NULL,
  `CREATED_ON` bigint(20) NOT NULL,
  `PARAMS` text NOT NULL,
  `IS_LOCKED` tinyint(1) NOT NULL DEFAULT '0',
  `IS_VALID` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `mh_plan_code_unq_idx` (`CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_plan`
--

LOCK TABLES `mh_plan` WRITE;
/*!40000 ALTER TABLE `mh_plan` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_snippet`
--

DROP TABLE IF EXISTS `mh_snippet`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_snippet` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `SNIPPET_CODE` varchar(100) NOT NULL,
  `SNIPPET_TYPE` varchar(50) NOT NULL,
  `PARAMS` mediumtext NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `mh_snippet_snippet_code_unq_idx` (`SNIPPET_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_snippet`
--

LOCK TABLES `mh_snippet` WRITE;
/*!40000 ALTER TABLE `mh_snippet` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_snippet` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_dispatcher`
--

DROP TABLE IF EXISTS `mh_processor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_processor` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `UPDATED_ON` bigint(20) NOT NULL,
  `IP` varchar(30) DEFAULT NULL,
  `DESCRIPTION` varchar(250) DEFAULT NULL,
  `STATUS` text NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_processor`
--

LOCK TABLES `mh_processor` WRITE;
/*!40000 ALTER TABLE `mh_processor` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_processor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_task`
--

DROP TABLE IF EXISTS `mh_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_task` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `PARAMS` mediumtext NOT NULL,
  `PROCESSOR_ID` decimal(10,0) DEFAULT NULL,
  `ASSIGNED_ON` bigint(20) DEFAULT NULL,
  `IS_COMPLETED` tinyint(1) NOT NULL DEFAULT '0',
  `COMPLETED_ON` bigint(20) DEFAULT NULL,
  `SNIPPET_EXEC_RESULTS` mediumtext,
  `METRICS` mediumtext,
  `TASK_ORDER` smallint(6) NOT NULL,
  `WORKBOOK_ID` decimal(10,0) NOT NULL,
  `EXEC_STATE` tinyint(1) NOT NULL DEFAULT '0',
  `IS_RESULT_RECEIVED` tinyint(1) NOT NULL DEFAULT '0',
  `RESULT_RESOURCE_SCHEDULED_ON` bigint(20) DEFAULT NULL,
  `PROCESS_TYPE` tinyint(1) NOT NULL,
  `EXTENDED_RESULT` mediumtext,
  PRIMARY KEY (`ID`),
  KEY `mh_task_workbook_id_idx` (`WORKBOOK_ID`),
  KEY `mh_task_workbook_id_task_order_idx` (`WORKBOOK_ID`,`TASK_ORDER`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_task`
--

LOCK TABLES `mh_task` WRITE;
/*!40000 ALTER TABLE `mh_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mh_workbook`
--

DROP TABLE IF EXISTS `mh_workbook`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_workbook` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `VERSION` int(10) unsigned NOT NULL,
  `PLAN_ID` decimal(10,0) NOT NULL,
  `CREATED_ON` bigint(20) NOT NULL,
  `COMPLETED_ON` bigint(20) DEFAULT NULL,
  `INPUT_RESOURCE_PARAM` longtext NOT NULL,
  `PRODUCING_ORDER` int(11) NOT NULL,
  `IS_VALID` tinyint(1) NOT NULL DEFAULT '0',
  `EXEC_STATE` smallint(6) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mh_workbook`
--

LOCK TABLES `mh_workbook` WRITE;
/*!40000 ALTER TABLE `mh_workbook` DISABLE KEYS */;
/*!40000 ALTER TABLE `mh_workbook` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2020-03-03  8:02:31
