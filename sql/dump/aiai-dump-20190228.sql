-- MySQL dump 10.13  Distrib 8.0.13, for Win64 (x86_64)
--
-- Host: localhost    Database: aiai_test
-- ------------------------------------------------------
-- Server version	8.0.13

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
 SET NAMES utf8 ;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `aiai_account`
--

DROP TABLE IF EXISTS MH_account;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_account` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `VERSION` decimal(5,0) NOT NULL,
  `USERNAME` varchar(30) COLLATE utf8_unicode_ci NOT NULL,
  `TOKEN` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `PASSWORD` varchar(100) COLLATE utf8_unicode_ci NOT NULL,
  `ROLES` varchar(100) COLLATE utf8_unicode_ci DEFAULT NULL,
  `PUBLIC_NAME` varchar(100) COLLATE utf8_unicode_ci DEFAULT NULL,
  `is_acc_not_expired` tinyint(1) NOT NULL DEFAULT '1',
  `is_not_locked` tinyint(1) NOT NULL DEFAULT '0',
  `is_cred_not_expired` tinyint(1) NOT NULL DEFAULT '0',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `mail_address` varchar(100) COLLATE utf8_unicode_ci DEFAULT NULL,
  `PHONE` varchar(100) COLLATE utf8_unicode_ci DEFAULT NULL,
  `PHONE_AS_STR` varchar(100) COLLATE utf8_unicode_ci DEFAULT NULL,
  `CREATED_ON` bigint(20) NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_account`
--

LOCK TABLES MH_account WRITE;
/*!40000 ALTER TABLE MH_account DISABLE KEYS */;
/*!40000 ALTER TABLE MH_account ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_log_data`
--

DROP TABLE IF EXISTS `aiai_log_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_log_data` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `REF_ID` decimal(10,0) NOT NULL,
  `VERSION` decimal(5,0) NOT NULL,
  `UPDATE_TS` timestamp NULL DEFAULT '0000-00-00 00:00:00' ON UPDATE CURRENT_TIMESTAMP,
  `LOG_TYPE` decimal(5,0) NOT NULL,
  `LOG_DATA` mediumtext COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_log_data`
--

LOCK TABLES `aiai_log_data` WRITE;
/*!40000 ALTER TABLE `aiai_log_data` DISABLE KEYS */;
/*!40000 ALTER TABLE `aiai_log_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_data`
--

DROP TABLE IF EXISTS `aiai_lp_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_data` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `CODE` varchar(200) COLLATE utf8_unicode_ci NOT NULL,
  `POOL_CODE` varchar(250) COLLATE utf8_unicode_ci NOT NULL,
  `DATA_TYPE` decimal(2,0) NOT NULL,
  `VERSION` decimal(5,0) NOT NULL,
  `WORKBOOK_ID` decimal(10,0) DEFAULT NULL,
  `UPLOAD_TS` timestamp NULL DEFAULT '0000-00-00 00:00:00' ON UPDATE CURRENT_TIMESTAMP,
  `DATA` longblob,
  `CHECKSUM` varchar(2048) COLLATE utf8_unicode_ci DEFAULT NULL,
  `IS_VALID` tinyint(1) NOT NULL DEFAULT '0',
  `IS_MANUAL` tinyint(1) NOT NULL DEFAULT '0',
  `FILENAME` varchar(150) COLLATE utf8_unicode_ci DEFAULT NULL,
  `STORAGE_URL` varchar(250) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `AIAI_LP_DATA_CODE_UNQ_IDX` (`CODE`),
  KEY `AIAI_LP_DATA_POOL_CODE_ID_IDX` (`POOL_CODE`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_data`
--

LOCK TABLES `aiai_lp_data` WRITE;
/*!40000 ALTER TABLE `aiai_lp_data` DISABLE KEYS */;
INSERT INTO `aiai_lp_data` VALUES (4,'simple-metrics.fit:1.1','simple-metrics.fit:1.1',2,0,NULL,'2019-02-28 08:32:22',_binary 'import gc\r\nimport os\r\nimport sys\r\n\r\nimport yaml\r\nfrom datetime import datetime\r\n\r\nfrom keras import backend as K\r\n\r\n\r\nclass Logger(object):\r\n    def __init__(self, artifact_path):\r\n        self.terminal = sys.stdout\r\n        self.log = open(os.path.join(artifact_path, \"logfile-fit.log\"), \"w\")\r\n\r\n    def write(self, message):\r\n        self.terminal.write(message)\r\n        self.log.write(message)\r\n        self.log.flush()\r\n\r\n    def flush(self):\r\n        # this flush method is needed for python 3 compatibility.\r\n        # this handles the flush command by doing nothing.\r\n        # you might want to specify some extra behavior here.\r\n        pass\r\n\r\n\r\nprint(sys.argv)\r\ncwd = os.getcwd()\r\n\r\nartifact_path = os.path.join(cwd, \'artifacts\')\r\nsys.stdout = Logger(artifact_path)\r\n\r\nyaml_file = os.path.join(artifact_path, \'params.yaml\')\r\n\r\nwith open(yaml_file, \'r\') as stream:\r\n    params = (yaml.load(stream))\r\n\r\n\r\noutput_file_path = params[\'outputResourceAbsolutePath\']\r\nwith open(output_file_path, \'w\') as output_file:\r\n    output_file.write(\"Ok\")\r\n    output_file.close()\r\n\r\n\r\nprint(params[\'hyperParams\'])\r\nprint(\'Done.\')\r\nprint(str(datetime.now()))\r\n\r\nK.clear_session()\r\ngc.collect()\r\n',NULL,1,0,NULL,'launchpad://'),(5,'simple-metrics.predict:1.1','simple-metrics.predict:1.1',2,0,NULL,'2019-02-28 08:32:22',_binary 'import gc\r\nimport os\r\nimport sys\r\nfrom random import randint\r\n\r\nimport yaml\r\nfrom datetime import datetime\r\nfrom keras import backend as K\r\n\r\n\r\nclass Logger(object):\r\n    def __init__(self, artifact_path):\r\n        self.terminal = sys.stdout\r\n        self.log = open(os.path.join(artifact_path, \"logfile-predict.log\"), \"w\")\r\n\r\n    def write(self, message):\r\n        self.terminal.write(message)\r\n        self.log.write(message)\r\n        self.log.flush()\r\n\r\n    def flush(self):\r\n        # this flush method is needed for python 3 compatibility.\r\n        # this handles the flush command by doing nothing.\r\n        # you might want to specify some extra behavior here.\r\n        pass\r\n\r\n\r\nprint(sys.argv)\r\ncwd = os.getcwd()\r\n\r\nartifact_path = os.path.join(cwd, \'artifacts\')\r\nsys.stdout = Logger(artifact_path)\r\n\r\nyaml_file = os.path.join(artifact_path, \'params.yaml\')\r\n\r\nwith open(yaml_file, \'r\') as stream:\r\n    params = (yaml.load(stream))\r\n\r\nprint(params[\'hyperParams\'])\r\n\r\noutput_file_path = params[\'outputResourceAbsolutePath\']\r\nwith open(output_file_path, \'w\') as output_file:\r\n    output_file.write(\"Ok\")\r\n    output_file.close()\r\n\r\nprint(str(datetime.now()))\r\n\r\n\r\nmetrics = {}\r\nmetricValues = {}\r\nmetrics[\'values\'] = metricValues\r\n\r\nmetricValues[\'sum\'] = randint(100, 200)\r\nmetrics_yaml_file = os.path.join(artifact_path, \'metrics.yaml\')\r\n\r\nwith open(metrics_yaml_file, \'w\') as outfile:\r\n    yaml.dump(metrics, outfile, default_plan_style=False)\r\n\r\n\r\nK.clear_session()\r\ngc.collect()\r\n',NULL,1,0,NULL,'launchpad://'),(6,'simple-dataset-IRIS_input_output_v2.txt','simple-dataset',1,0,NULL,'2019-02-28 08:33:28',_binary '5.1,3.5,1.4,0.2,1,0,0\r\n4.9,3.0,1.4,0.2,1,0,0\r\n4.7,3.2,1.3,0.2,1,0,0\r\n4.6,3.1,1.5,0.2,1,0,0\r\n5.0,3.6,1.4,0.2,1,0,0\r\n5.4,3.9,1.7,0.4,1,0,0\r\n4.6,3.4,1.4,0.3,1,0,0\r\n5.0,3.4,1.5,0.2,1,0,0\r\n4.4,2.9,1.4,0.2,1,0,0\r\n4.9,3.1,1.5,0.1,1,0,0\r\n5.4,3.7,1.5,0.2,1,0,0\r\n4.8,3.4,1.6,0.2,1,0,0\r\n4.8,3.0,1.4,0.1,1,0,0\r\n4.3,3.0,1.1,0.1,1,0,0\r\n5.8,4.0,1.2,0.2,1,0,0\r\n5.7,4.4,1.5,0.4,1,0,0\r\n5.4,3.9,1.3,0.4,1,0,0\r\n5.1,3.5,1.4,0.3,1,0,0\r\n5.7,3.8,1.7,0.3,1,0,0\r\n5.1,3.8,1.5,0.3,1,0,0\r\n5.4,3.4,1.7,0.2,1,0,0\r\n5.1,3.7,1.5,0.4,1,0,0\r\n4.6,3.6,1.0,0.2,1,0,0\r\n5.1,3.3,1.7,0.5,1,0,0\r\n4.8,3.4,1.9,0.2,1,0,0\r\n5.0,3.0,1.6,0.2,1,0,0\r\n5.0,3.4,1.6,0.4,1,0,0\r\n5.2,3.5,1.5,0.2,1,0,0\r\n5.2,3.4,1.4,0.2,1,0,0\r\n4.7,3.2,1.6,0.2,1,0,0\r\n4.8,3.1,1.6,0.2,1,0,0\r\n5.4,3.4,1.5,0.4,1,0,0\r\n5.2,4.1,1.5,0.1,1,0,0\r\n5.5,4.2,1.4,0.2,1,0,0\r\n4.9,3.1,1.5,0.1,1,0,0\r\n5.0,3.2,1.2,0.2,1,0,0\r\n5.5,3.5,1.3,0.2,1,0,0\r\n4.9,3.1,1.5,0.1,1,0,0\r\n4.4,3.0,1.3,0.2,1,0,0\r\n5.1,3.4,1.5,0.2,1,0,0\r\n5.0,3.5,1.3,0.3,1,0,0\r\n4.5,2.3,1.3,0.3,1,0,0\r\n4.4,3.2,1.3,0.2,1,0,0\r\n5.0,3.5,1.6,0.6,1,0,0\r\n5.1,3.8,1.9,0.4,1,0,0\r\n4.8,3.0,1.4,0.3,1,0,0\r\n5.1,3.8,1.6,0.2,1,0,0\r\n4.6,3.2,1.4,0.2,1,0,0\r\n5.3,3.7,1.5,0.2,1,0,0\r\n5.0,3.3,1.4,0.2,1,0,0\r\n7.0,3.2,4.7,1.4,0,1,0\r\n6.4,3.2,4.5,1.5,0,1,0\r\n6.9,3.1,4.9,1.5,0,1,0\r\n5.5,2.3,4.0,1.3,0,1,0\r\n6.5,2.8,4.6,1.5,0,1,0\r\n5.7,2.8,4.5,1.3,0,1,0\r\n6.3,3.3,4.7,1.6,0,1,0\r\n4.9,2.4,3.3,1.0,0,1,0\r\n6.6,2.9,4.6,1.3,0,1,0\r\n5.2,2.7,3.9,1.4,0,1,0\r\n5.0,2.0,3.5,1.0,0,1,0\r\n5.9,3.0,4.2,1.5,0,1,0\r\n6.0,2.2,4.0,1.0,0,1,0\r\n6.1,2.9,4.7,1.4,0,1,0\r\n5.6,2.9,3.6,1.3,0,1,0\r\n6.7,3.1,4.4,1.4,0,1,0\r\n5.6,3.0,4.5,1.5,0,1,0\r\n5.8,2.7,4.1,1.0,0,1,0\r\n6.2,2.2,4.5,1.5,0,1,0\r\n5.6,2.5,3.9,1.1,0,1,0\r\n5.9,3.2,4.8,1.8,0,1,0\r\n6.1,2.8,4.0,1.3,0,1,0\r\n6.3,2.5,4.9,1.5,0,1,0\r\n6.1,2.8,4.7,1.2,0,1,0\r\n6.4,2.9,4.3,1.3,0,1,0\r\n6.6,3.0,4.4,1.4,0,1,0\r\n6.8,2.8,4.8,1.4,0,1,0\r\n6.7,3.0,5.0,1.7,0,1,0\r\n6.0,2.9,4.5,1.5,0,1,0\r\n5.7,2.6,3.5,1.0,0,1,0\r\n5.5,2.4,3.8,1.1,0,1,0\r\n5.5,2.4,3.7,1.0,0,1,0\r\n5.8,2.7,3.9,1.2,0,1,0\r\n6.0,2.7,5.1,1.6,0,1,0\r\n5.4,3.0,4.5,1.5,0,1,0\r\n6.0,3.4,4.5,1.6,0,1,0\r\n6.7,3.1,4.7,1.5,0,1,0\r\n6.3,2.3,4.4,1.3,0,1,0\r\n5.6,3.0,4.1,1.3,0,1,0\r\n5.5,2.5,4.0,1.3,0,1,0\r\n5.5,2.6,4.4,1.2,0,1,0\r\n6.1,3.0,4.6,1.4,0,1,0\r\n5.8,2.6,4.0,1.2,0,1,0\r\n5.0,2.3,3.3,1.0,0,1,0\r\n5.6,2.7,4.2,1.3,0,1,0\r\n5.7,3.0,4.2,1.2,0,1,0\r\n5.7,2.9,4.2,1.3,0,1,0\r\n6.2,2.9,4.3,1.3,0,1,0\r\n5.1,2.5,3.0,1.1,0,1,0\r\n5.7,2.8,4.1,1.3,0,1,0\r\n6.3,3.3,6.0,2.5,0,0,1\r\n5.8,2.7,5.1,1.9,0,0,1\r\n7.1,3.0,5.9,2.1,0,0,1\r\n6.3,2.9,5.6,1.8,0,0,1\r\n6.5,3.0,5.8,2.2,0,0,1\r\n7.6,3.0,6.6,2.1,0,0,1\r\n4.9,2.5,4.5,1.7,0,0,1\r\n7.3,2.9,6.3,1.8,0,0,1\r\n6.7,2.5,5.8,1.8,0,0,1\r\n7.2,3.6,6.1,2.5,0,0,1\r\n6.5,3.2,5.1,2.0,0,0,1\r\n6.4,2.7,5.3,1.9,0,0,1\r\n6.8,3.0,5.5,2.1,0,0,1\r\n5.7,2.5,5.0,2.0,0,0,1\r\n5.8,2.8,5.1,2.4,0,0,1\r\n6.4,3.2,5.3,2.3,0,0,1\r\n6.5,3.0,5.5,1.8,0,0,1\r\n7.7,3.8,6.7,2.2,0,0,1\r\n7.7,2.6,6.9,2.3,0,0,1\r\n6.0,2.2,5.0,1.5,0,0,1\r\n6.9,3.2,5.7,2.3,0,0,1\r\n5.6,2.8,4.9,2.0,0,0,1\r\n7.7,2.8,6.7,2.0,0,0,1\r\n6.3,2.7,4.9,1.8,0,0,1\r\n6.7,3.3,5.7,2.1,0,0,1\r\n7.2,3.2,6.0,1.8,0,0,1\r\n6.2,2.8,4.8,1.8,0,0,1\r\n6.1,3.0,4.9,1.8,0,0,1\r\n6.4,2.8,5.6,2.1,0,0,1\r\n7.2,3.0,5.8,1.6,0,0,1\r\n7.4,2.8,6.1,1.9,0,0,1\r\n7.9,3.8,6.4,2.0,0,0,1\r\n6.4,2.8,5.6,2.2,0,0,1\r\n6.3,2.8,5.1,1.5,0,0,1\r\n6.1,2.6,5.6,1.4,0,0,1\r\n7.7,3.0,6.1,2.3,0,0,1\r\n6.3,3.4,5.6,2.4,0,0,1\r\n6.4,3.1,5.5,1.8,0,0,1\r\n6.0,3.0,4.8,1.8,0,0,1\r\n6.9,3.1,5.4,2.1,0,0,1\r\n6.7,3.1,5.6,2.4,0,0,1\r\n6.9,3.1,5.1,2.3,0,0,1\r\n5.8,2.7,5.1,1.9,0,0,1\r\n6.8,3.2,5.9,2.3,0,0,1\r\n6.7,3.3,5.7,2.5,0,0,1\r\n6.7,3.0,5.2,2.3,0,0,1\r\n6.3,2.5,5.0,1.9,0,0,1\r\n6.5,3.0,5.2,2.0,0,0,1\r\n6.2,3.4,5.4,2.3,0,0,1\r\n5.9,3.0,5.1,1.8,0,0,1\r\n',NULL,1,1,'IRIS_input_output_v2.txt','launchpad://'),(7,'task-1-ml_model.bin','task-1-ml_model.bin',1,0,4,'2019-02-28 08:38:17',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(8,'task-3-ml_model.bin','task-3-ml_model.bin',1,0,4,'2019-02-28 08:38:51',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(9,'task-5-ml_model.bin','task-5-ml_model.bin',1,0,4,'2019-02-28 08:39:24',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(10,'task-7-ml_model.bin','task-7-ml_model.bin',1,0,4,'2019-02-28 08:39:58',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(11,'task-9-ml_model.bin','task-9-ml_model.bin',1,0,4,'2019-02-28 08:40:31',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(12,'task-11-ml_model.bin','task-11-ml_model.bin',1,0,4,'2019-02-28 08:41:01',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(13,'task-13-ml_model.bin','task-13-ml_model.bin',1,0,4,'2019-02-28 08:41:35',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(14,'task-15-ml_model.bin','task-15-ml_model.bin',1,0,4,'2019-02-28 08:41:59',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(15,'task-2-output-stub-for-predict','task-2-output-stub-for-predict',1,0,4,'2019-02-28 08:42:41',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(16,'task-4-output-stub-for-predict','task-4-output-stub-for-predict',1,0,4,'2019-02-28 08:43:14',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(17,'task-6-output-stub-for-predict','task-6-output-stub-for-predict',1,0,4,'2019-02-28 08:43:48',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(18,'task-8-output-stub-for-predict','task-8-output-stub-for-predict',1,0,4,'2019-02-28 08:44:21',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(19,'task-10-output-stub-for-predict','task-10-output-stub-for-predict',1,0,4,'2019-02-28 08:44:54',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(20,'task-12-output-stub-for-predict','task-12-output-stub-for-predict',1,0,4,'2019-02-28 08:45:27',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(21,'task-14-output-stub-for-predict','task-14-output-stub-for-predict',1,0,4,'2019-02-28 08:46:01',_binary 'Ok',NULL,1,0,NULL,'launchpad://'),(22,'task-16-output-stub-for-predict','task-16-output-stub-for-predict',1,0,4,'2019-02-28 08:46:22',_binary 'Ok',NULL,1,0,NULL,'launchpad://');
/*!40000 ALTER TABLE `aiai_lp_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_experiment`
--

DROP TABLE IF EXISTS `aiai_lp_experiment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_experiment` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `VERSION` decimal(5,0) NOT NULL,
  `WORKBOOK_ID` decimal(10,0) DEFAULT NULL,
  `NAME` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `DESCRIPTION` varchar(250) COLLATE utf8_unicode_ci NOT NULL,
  `CODE` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `SEED` int(10) DEFAULT NULL,
  `NUMBER_OF_TASK` int(10) NOT NULL DEFAULT '0',
  `IS_ALL_TASK_PRODUCED` tinyint(1) NOT NULL DEFAULT '0',
  `IS_FEATURE_PRODUCED` tinyint(1) NOT NULL DEFAULT '0',
  `CREATED_ON` bigint(20) NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `AIAI_LP_EXPERIMENT_CODE_UNQ_IDX` (`CODE`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_experiment`
--

LOCK TABLES `aiai_lp_experiment` WRITE;
/*!40000 ALTER TABLE `aiai_lp_experiment` DISABLE KEYS */;
INSERT INTO `aiai_lp_experiment` VALUES (1,2,4,'experiment-1','experiment-1','experiment-1',1,16,1,1,0);
/*!40000 ALTER TABLE `aiai_lp_experiment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_experiment_feature`
--

DROP TABLE IF EXISTS `aiai_lp_experiment_feature`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_experiment_feature` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `EXPERIMENT_ID` decimal(10,0) NOT NULL,
  `VERSION` decimal(5,0) NOT NULL,
  `RESOURCE_CODES` varchar(2048) COLLATE utf8_unicode_ci NOT NULL,
  `CHECKSUM_ID_CODES` varchar(100) COLLATE utf8_unicode_ci NOT NULL,
  `EXEC_STATUS` tinyint(1) NOT NULL DEFAULT '0',
  `MAX_VALUE` decimal(10,4) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `AIAI_LP_EXPERIMENT_FEATURE_UNQ_IDX` (`EXPERIMENT_ID`,`CHECKSUM_ID_CODES`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_experiment_feature`
--

LOCK TABLES `aiai_lp_experiment_feature` WRITE;
/*!40000 ALTER TABLE `aiai_lp_experiment_feature` DISABLE KEYS */;
INSERT INTO `aiai_lp_experiment_feature` VALUES (1,1,1,'[simple-dataset-IRIS_input_output_v2.txt]','[simple-dataset-IRIS###cae53501f87c8c87d3ae268a5af3cb3a',0,159.0000);
/*!40000 ALTER TABLE `aiai_lp_experiment_feature` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_experiment_hyper_params`
--

DROP TABLE IF EXISTS `aiai_lp_experiment_hyper_params`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_experiment_hyper_params` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `EXPERIMENT_ID` decimal(10,0) NOT NULL,
  `VERSION` decimal(5,0) NOT NULL,
  `HYPER_PARAM_KEY` varchar(50) COLLATE utf8_unicode_ci DEFAULT NULL,
  `HYPER_PARAM_VALUES` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_experiment_hyper_params`
--

LOCK TABLES `aiai_lp_experiment_hyper_params` WRITE;
/*!40000 ALTER TABLE `aiai_lp_experiment_hyper_params` DISABLE KEYS */;
INSERT INTO `aiai_lp_experiment_hyper_params` VALUES (8,1,0,'aaa','[10,20]'),(9,1,0,'bbb','[bbb_1, bbb_2]'),(10,1,0,'ccc','[ccc_1, ccc_2]'),(11,1,0,'ddd','10');
/*!40000 ALTER TABLE `aiai_lp_experiment_hyper_params` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_experiment_snippet`
--

DROP TABLE IF EXISTS `aiai_lp_experiment_snippet`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_experiment_snippet` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `EXPERIMENT_ID` decimal(10,0) NOT NULL,
  `VERSION` decimal(5,0) NOT NULL,
  `SNIPPET_CODE` varchar(100) COLLATE utf8_unicode_ci NOT NULL,
  `SNIPPET_TYPE` varchar(20) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`ID`),
  KEY `AIAI_LP_EXPERIMENT_SNIPPET_EXPERIMENT_ID_IDX` (`EXPERIMENT_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_experiment_snippet`
--

LOCK TABLES `aiai_lp_experiment_snippet` WRITE;
/*!40000 ALTER TABLE `aiai_lp_experiment_snippet` DISABLE KEYS */;
INSERT INTO `aiai_lp_experiment_snippet` VALUES (3,1,0,'simple-metrics.fit:1.1','fit'),(4,1,0,'simple-metrics.predict:1.1','predict');
/*!40000 ALTER TABLE `aiai_lp_experiment_snippet` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_experiment_task`
--

DROP TABLE IF EXISTS `aiai_lp_experiment_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_experiment_task` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `VERSION` decimal(5,0) NOT NULL,
  `WORKBOOK_ID` decimal(10,0) NOT NULL,
  `FIT_TASK_ID` decimal(10,0) NOT NULL,
  `PREDICT_TASK_ID` decimal(10,0) NOT NULL,
  `FIT_EXEC_STATE` tinyint(1) NOT NULL DEFAULT '0',
  `PREDICT_EXEC_STATE` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_experiment_task`
--

LOCK TABLES `aiai_lp_experiment_task` WRITE;
/*!40000 ALTER TABLE `aiai_lp_experiment_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `aiai_lp_experiment_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_experiment_task_feature`
--

DROP TABLE IF EXISTS `aiai_lp_experiment_task_feature`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_experiment_task_feature` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `VERSION` decimal(5,0) NOT NULL,
  `WORKBOOK_ID` decimal(10,0) NOT NULL,
  `TASK_ID` decimal(10,0) NOT NULL,
  `FEATURE_ID` decimal(10,0) NOT NULL,
  `TASK_TYPE` tinyint(1) NOT NULL,
  PRIMARY KEY (`ID`),
  KEY `AIAI_LP_EXPERIMENT_TASK_FEATURE_WORKBOOK_ID_IDX` (`WORKBOOK_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_experiment_task_feature`
--

LOCK TABLES `aiai_lp_experiment_task_feature` WRITE;
/*!40000 ALTER TABLE `aiai_lp_experiment_task_feature` DISABLE KEYS */;
INSERT INTO `aiai_lp_experiment_task_feature` VALUES (1,0,4,1,1,1),(2,0,4,2,1,2),(3,0,4,3,1,1),(4,0,4,4,1,2),(5,0,4,5,1,1),(6,0,4,6,1,2),(7,0,4,7,1,1),(8,0,4,8,1,2),(9,0,4,9,1,1),(10,0,4,10,1,2),(11,0,4,11,1,1),(12,0,4,12,1,2),(13,0,4,13,1,1),(14,0,4,14,1,2),(15,0,4,15,1,1),(16,0,4,16,1,2);
/*!40000 ALTER TABLE `aiai_lp_experiment_task_feature` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_plan`
--

DROP TABLE IF EXISTS `aiai_lp_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_plan` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `VERSION` decimal(5,0) NOT NULL,
  `CODE` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `CREATED_ON` bigint(20) NOT NULL,
  `PARAMS` text COLLATE utf8_unicode_ci NOT NULL,
  `IS_LOCKED` tinyint(1) NOT NULL DEFAULT '0',
  `IS_VALID` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_plan`
--

LOCK TABLES `aiai_lp_plan` WRITE;
/*!40000 ALTER TABLE `aiai_lp_plan` DISABLE KEYS */;
INSERT INTO `aiai_lp_plan` VALUES (3,3,'simple-experiment',0,'processes:\r\n- code: experiment-1\r\n  collectResources: true\r\n  name: experiment\r\n  parallelExec: false\r\n  type: EXPERIMENT\r\n  metas:\r\n  - key: feature\r\n    value: simple-dataset',1,1);
/*!40000 ALTER TABLE `aiai_lp_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_plan_instance`
--

DROP TABLE IF EXISTS `aiai_lp_plan_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_plan_instance` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `VERSION` decimal(5,0) NOT NULL,
  `PLAN_ID` decimal(10,0) NOT NULL,
  `CREATED_ON` bigint(20) NOT NULL,
  `COMPLETED_ON` bigint(20) DEFAULT NULL,
  `INPUT_RESOURCE_PARAM` text COLLATE utf8_unicode_ci NOT NULL,
  `PRODUCING_ORDER` int(11) NOT NULL,
  `IS_VALID` tinyint(1) NOT NULL DEFAULT '0',
  `EXEC_STATE` smallint(6) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_plan_instance`
--

LOCK TABLES `aiai_lp_plan_instance` WRITE;
/*!40000 ALTER TABLE `aiai_lp_plan_instance` DISABLE KEYS */;
INSERT INTO `aiai_lp_plan_instance` VALUES (4,6,3,1551343003138,1551343588274,'poolCodes:\n  workbook-input-type:\n  - simple-dataset',3,1,5);
/*!40000 ALTER TABLE `aiai_lp_plan_instance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_launchpad_address`
--

DROP TABLE IF EXISTS `aiai_lp_launchpad_address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_launchpad_address` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `VERSION` decimal(5,0) NOT NULL,
  `URL` varchar(200) COLLATE utf8_unicode_ci NOT NULL,
  `DESCRIPTION` varchar(100) COLLATE utf8_unicode_ci NOT NULL,
  `SIGNATURE` varchar(1000) COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_launchpad_address`
--

LOCK TABLES `aiai_lp_launchpad_address` WRITE;
/*!40000 ALTER TABLE `aiai_lp_launchpad_address` DISABLE KEYS */;
/*!40000 ALTER TABLE `aiai_lp_launchpad_address` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_snippet`
--

DROP TABLE IF EXISTS `aiai_lp_snippet`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_snippet` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `VERSION` decimal(5,0) NOT NULL,
  `NAME` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `SNIPPET_TYPE` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `SNIPPET_VERSION` varchar(20) COLLATE utf8_unicode_ci NOT NULL,
  `FILENAME` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  `CHECKSUM` varchar(2048) COLLATE utf8_unicode_ci DEFAULT NULL,
  `IS_SIGNED` tinyint(1) NOT NULL DEFAULT '0',
  `IS_REPORT_METRICS` tinyint(1) NOT NULL DEFAULT '0',
  `ENV` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `PARAMS` varchar(1000) COLLATE utf8_unicode_ci DEFAULT NULL,
  `CODE_LENGTH` int(11) NOT NULL,
  `IS_FILE_PROVIDED` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `AIAI_LP_SNIPPET_UNQ_IDX` (`NAME`,`SNIPPET_VERSION`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_snippet`
--

LOCK TABLES `aiai_lp_snippet` WRITE;
/*!40000 ALTER TABLE `aiai_lp_snippet` DISABLE KEYS */;
INSERT INTO `aiai_lp_snippet` VALUES (3,0,'simple-metrics.fit','fit','1.1','simple-metrics-fit.py','{\"checksums\":{\"SHA256\":\"845efccfb2728e7f90ce665027e6aceedf32871892f16982b68d9404f23ccd17\"}}',0,0,'python-3',NULL,1189,0),(4,0,'simple-metrics.predict','predict','1.1','simple-metrics-predict.py','{\"checksums\":{\"SHA256\":\"2bb8fff0bd8ed9e52060454c9e1a4a9be0018afb138a4419cd725e1d45179966\"}}',0,1,'python-3',NULL,1490,0);
/*!40000 ALTER TABLE `aiai_lp_snippet` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_station`
--

DROP TABLE IF EXISTS `aiai_lp_station`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_station` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `VERSION` decimal(5,0) NOT NULL,
  `IP` varchar(30) COLLATE utf8_unicode_ci DEFAULT NULL,
  `UPDATE_TS` timestamp NULL DEFAULT '0000-00-00 00:00:00' ON UPDATE CURRENT_TIMESTAMP,
  `DESCRIPTION` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  `ENV` mediumtext COLLATE utf8_unicode_ci,
  `ACTIVE_TIME` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_station`
--

LOCK TABLES `aiai_lp_station` WRITE;
/*!40000 ALTER TABLE `aiai_lp_station` DISABLE KEYS */;
INSERT INTO `aiai_lp_station` VALUES (1,1,NULL,'2019-02-28 03:18:28',NULL,'envs:\r\n  python-3: C:\\Anaconda3\\envs\\python-3.7\\python.exe\r\n  java-8: C:\\jdk1.8.0_191\\bin\\java.exe -jar\r\n  type-cmd: java -jar C:\\sandbox\\git\\aiai\\apps\\simple-app\\target\\simple-app.jar\r\n',NULL);
/*!40000 ALTER TABLE `aiai_lp_station` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiai_lp_task`
--

DROP TABLE IF EXISTS `aiai_lp_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `aiai_lp_task` (
  `ID` int(10) NOT NULL AUTO_INCREMENT,
  `VERSION` decimal(5,0) NOT NULL,
  `PARAMS` mediumtext COLLATE utf8_unicode_ci NOT NULL,
  `STATION_ID` decimal(10,0) DEFAULT NULL,
  `ASSIGNED_ON` bigint(20) DEFAULT NULL,
  `IS_COMPLETED` tinyint(1) NOT NULL DEFAULT '0',
  `COMPLETED_ON` bigint(20) DEFAULT NULL,
  `SNIPPET_EXEC_RESULTS` mediumtext COLLATE utf8_unicode_ci,
  `METRICS` mediumtext COLLATE utf8_unicode_ci,
  `TASK_ORDER` smallint(6) NOT NULL,
  `WORKBOOK_ID` decimal(10,0) NOT NULL,
  `EXEC_STATE` tinyint(1) NOT NULL DEFAULT '0',
  `IS_RESULT_RECEIVED` tinyint(1) NOT NULL DEFAULT '0',
  `RESULT_RESOURCE_SCHEDULED_ON` bigint(20) DEFAULT NULL,
  `PROCESS_TYPE` tinyint(1) NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiai_lp_task`
--

LOCK TABLES `aiai_lp_task` WRITE;
/*!40000 ALTER TABLE `aiai_lp_task` DISABLE KEYS */;
INSERT INTO `aiai_lp_task` VALUES (1,4,'clean: false\nhyperParams:\n  aaa: \'10\'\n  bbb: bbb_1\n  ccc: ccc_1\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\noutputResourceAbsolutePath: null\noutputResourceCode: task-1-ml_model.bin\nresourceStorageUrls:\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-1-ml_model.bin: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"845efccfb2728e7f90ce665027e6aceedf32871892f16982b68d9404f23ccd17\"}}\'\n  code: simple-metrics.fit:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-fit.py\n  metrics: false\n  params: null\n  type: fit\nworkingPath: null\n',1,1551343063176,1,1551343097352,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.fit_1.1\\\\simple-metrics-fit.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\1\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'10\', \'bbb\': \'bbb_1\', \'ccc\': \'ccc_1\', \'ddd\': \'10\', \'seed\': \'1\'}\n    Done.\n    2019-02-28 00:38:14.743369\n  exitCode: 0\n  isOk: true\n  ok: true\n',NULL,1,4,3,1,1551343103434,2),(2,4,'clean: false\nhyperParams:\n  aaa: \'10\'\n  bbb: bbb_1\n  ccc: ccc_1\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\n  model:\n  - task-1-ml_model.bin\noutputResourceAbsolutePath: null\noutputResourceCode: task-2-output-stub-for-predict\nresourceStorageUrls:\n  task-2-output-stub-for-predict: launchpad://\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-1-ml_model.bin: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"2bb8fff0bd8ed9e52060454c9e1a4a9be0018afb138a4419cd725e1d45179966\"}}\'\n  code: simple-metrics.predict:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-predict.py\n  metrics: true\n  params: null\n  type: predict\nworkingPath: null\n',1,1551343336857,1,1551343361185,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.predict_1.1\\\\simple-metrics-predict.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\2\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'10\', \'bbb\': \'bbb_1\', \'ccc\': \'ccc_1\', \'ddd\': \'10\', \'seed\': \'1\'}\n    2019-02-28 00:42:40.000275\n  exitCode: 0\n  isOk: true\n  ok: true\n','error: null\nmetrics: \"values:\\r\\n  sum: 125\\r\\n\"\nstatus: Ok\n',2,4,3,1,1551343367048,2),(3,4,'clean: false\nhyperParams:\n  aaa: \'20\'\n  bbb: bbb_1\n  ccc: ccc_1\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\noutputResourceAbsolutePath: null\noutputResourceCode: task-3-ml_model.bin\nresourceStorageUrls:\n  task-3-ml_model.bin: launchpad://\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"845efccfb2728e7f90ce665027e6aceedf32871892f16982b68d9404f23ccd17\"}}\'\n  code: simple-metrics.fit:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-fit.py\n  metrics: false\n  params: null\n  type: fit\nworkingPath: null\n',1,1551343113656,1,1551343130769,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.fit_1.1\\\\simple-metrics-fit.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\3\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'20\', \'bbb\': \'bbb_1\', \'ccc\': \'ccc_1\', \'ddd\': \'10\', \'seed\': \'1\'}\n    Done.\n    2019-02-28 00:38:48.059787\n  exitCode: 0\n  isOk: true\n  ok: true\n',NULL,1,4,3,1,1551343133856,2),(4,4,'clean: false\nhyperParams:\n  aaa: \'20\'\n  bbb: bbb_1\n  ccc: ccc_1\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\n  model:\n  - task-3-ml_model.bin\noutputResourceAbsolutePath: null\noutputResourceCode: task-4-output-stub-for-predict\nresourceStorageUrls:\n  task-3-ml_model.bin: launchpad://\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-4-output-stub-for-predict: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"2bb8fff0bd8ed9e52060454c9e1a4a9be0018afb138a4419cd725e1d45179966\"}}\'\n  code: simple-metrics.predict:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-predict.py\n  metrics: true\n  params: null\n  type: predict\nworkingPath: null\n',1,1551343377307,1,1551343394476,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.predict_1.1\\\\simple-metrics-predict.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\4\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'20\', \'bbb\': \'bbb_1\', \'ccc\': \'ccc_1\', \'ddd\': \'10\', \'seed\': \'1\'}\n    2019-02-28 00:43:12.716472\n  exitCode: 0\n  isOk: true\n  ok: true\n','error: null\nmetrics: \"values:\\r\\n  sum: 143\\r\\n\"\nstatus: Ok\n',2,4,3,1,1551343397490,2),(5,4,'clean: false\nhyperParams:\n  aaa: \'10\'\n  bbb: bbb_1\n  ccc: ccc_2\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\noutputResourceAbsolutePath: null\noutputResourceCode: task-5-ml_model.bin\nresourceStorageUrls:\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-5-ml_model.bin: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"845efccfb2728e7f90ce665027e6aceedf32871892f16982b68d9404f23ccd17\"}}\'\n  code: simple-metrics.fit:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-fit.py\n  metrics: false\n  params: null\n  type: fit\nworkingPath: null\n',1,1551343144017,1,1551343164172,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.fit_1.1\\\\simple-metrics-fit.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\5\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'10\', \'bbb\': \'bbb_1\', \'ccc\': \'ccc_2\', \'ddd\': \'10\', \'seed\': \'1\'}\n    Done.\n    2019-02-28 00:39:21.134625\n  exitCode: 0\n  isOk: true\n  ok: true\n',NULL,1,4,3,1,1551343164378,2),(6,4,'clean: false\nhyperParams:\n  aaa: \'10\'\n  bbb: bbb_1\n  ccc: ccc_2\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\n  model:\n  - task-5-ml_model.bin\noutputResourceAbsolutePath: null\noutputResourceCode: task-6-output-stub-for-predict\nresourceStorageUrls:\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-5-ml_model.bin: launchpad://\n  task-6-output-stub-for-predict: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"2bb8fff0bd8ed9e52060454c9e1a4a9be0018afb138a4419cd725e1d45179966\"}}\'\n  code: simple-metrics.predict:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-predict.py\n  metrics: true\n  params: null\n  type: predict\nworkingPath: null\n',1,1551343407643,1,1551343427756,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.predict_1.1\\\\simple-metrics-predict.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\6\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'10\', \'bbb\': \'bbb_1\', \'ccc\': \'ccc_2\', \'ddd\': \'10\', \'seed\': \'1\'}\n    2019-02-28 00:43:45.400393\n  exitCode: 0\n  isOk: true\n  ok: true\n','error: null\nmetrics: \"values:\\r\\n  sum: 143\\r\\n\"\nstatus: Ok\n',2,4,3,1,1551343427888,2),(7,4,'clean: false\nhyperParams:\n  aaa: \'20\'\n  bbb: bbb_1\n  ccc: ccc_2\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\noutputResourceAbsolutePath: null\noutputResourceCode: task-7-ml_model.bin\nresourceStorageUrls:\n  task-7-ml_model.bin: launchpad://\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"845efccfb2728e7f90ce665027e6aceedf32871892f16982b68d9404f23ccd17\"}}\'\n  code: simple-metrics.fit:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-fit.py\n  metrics: false\n  params: null\n  type: fit\nworkingPath: null\n',1,1551343174538,1,1551343197694,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.fit_1.1\\\\simple-metrics-fit.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\7\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'20\', \'bbb\': \'bbb_1\', \'ccc\': \'ccc_2\', \'ddd\': \'10\', \'seed\': \'1\'}\n    Done.\n    2019-02-28 00:39:54.534733\n  exitCode: 0\n  isOk: true\n  ok: true\n',NULL,1,4,3,1,1551343204782,2),(8,4,'clean: false\nhyperParams:\n  aaa: \'20\'\n  bbb: bbb_1\n  ccc: ccc_2\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\n  model:\n  - task-7-ml_model.bin\noutputResourceAbsolutePath: null\noutputResourceCode: task-8-output-stub-for-predict\nresourceStorageUrls:\n  task-7-ml_model.bin: launchpad://\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-8-output-stub-for-predict: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"2bb8fff0bd8ed9e52060454c9e1a4a9be0018afb138a4419cd725e1d45179966\"}}\'\n  code: simple-metrics.predict:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-predict.py\n  metrics: true\n  params: null\n  type: predict\nworkingPath: null\n',1,1551343437991,1,1551343461077,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.predict_1.1\\\\simple-metrics-predict.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\8\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'20\', \'bbb\': \'bbb_1\', \'ccc\': \'ccc_2\', \'ddd\': \'10\', \'seed\': \'1\'}\n    2019-02-28 00:44:18.233901\n  exitCode: 0\n  isOk: true\n  ok: true\n','error: null\nmetrics: \"values:\\r\\n  sum: 159\\r\\n\"\nstatus: Ok\n',2,4,3,1,1551343468189,2),(9,4,'clean: false\nhyperParams:\n  aaa: \'10\'\n  bbb: bbb_2\n  ccc: ccc_1\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\noutputResourceAbsolutePath: null\noutputResourceCode: task-9-ml_model.bin\nresourceStorageUrls:\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-9-ml_model.bin: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"845efccfb2728e7f90ce665027e6aceedf32871892f16982b68d9404f23ccd17\"}}\'\n  code: simple-metrics.fit:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-fit.py\n  metrics: false\n  params: null\n  type: fit\nworkingPath: null\n',1,1551343214963,1,1551343231040,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.fit_1.1\\\\simple-metrics-fit.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\9\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'10\', \'bbb\': \'bbb_2\', \'ccc\': \'ccc_1\', \'ddd\': \'10\', \'seed\': \'1\'}\n    Done.\n    2019-02-28 00:40:28.292508\n  exitCode: 0\n  isOk: true\n  ok: true\n',NULL,1,4,3,1,1551343235279,2),(10,4,'clean: false\nhyperParams:\n  aaa: \'10\'\n  bbb: bbb_2\n  ccc: ccc_1\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\n  model:\n  - task-9-ml_model.bin\noutputResourceAbsolutePath: null\noutputResourceCode: task-10-output-stub-for-predict\nresourceStorageUrls:\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-9-ml_model.bin: launchpad://\n  task-10-output-stub-for-predict: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"2bb8fff0bd8ed9e52060454c9e1a4a9be0018afb138a4419cd725e1d45179966\"}}\'\n  code: simple-metrics.predict:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-predict.py\n  metrics: true\n  params: null\n  type: predict\nworkingPath: null\n',1,1551343478328,1,1551343494318,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.predict_1.1\\\\simple-metrics-predict.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\10\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'10\', \'bbb\': \'bbb_2\', \'ccc\': \'ccc_1\', \'ddd\': \'10\', \'seed\': \'1\'}\n    2019-02-28 00:44:51.324613\n  exitCode: 0\n  isOk: true\n  ok: true\n','error: null\nmetrics: \"values:\\r\\n  sum: 154\\r\\n\"\nstatus: Ok\n',2,4,3,1,1551343498550,2),(11,4,'clean: false\nhyperParams:\n  aaa: \'20\'\n  bbb: bbb_2\n  ccc: ccc_1\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\noutputResourceAbsolutePath: null\noutputResourceCode: task-11-ml_model.bin\nresourceStorageUrls:\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-11-ml_model.bin: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"845efccfb2728e7f90ce665027e6aceedf32871892f16982b68d9404f23ccd17\"}}\'\n  code: simple-metrics.fit:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-fit.py\n  metrics: false\n  params: null\n  type: fit\nworkingPath: null\n',1,1551343245435,1,1551343261360,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.fit_1.1\\\\simple-metrics-fit.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\11\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'20\', \'bbb\': \'bbb_2\', \'ccc\': \'ccc_1\', \'ddd\': \'10\', \'seed\': \'1\'}\n    Done.\n    2019-02-28 00:41:00.867081\n  exitCode: 0\n  isOk: true\n  ok: true\n',NULL,1,4,3,1,1551343265654,2),(12,4,'clean: false\nhyperParams:\n  aaa: \'20\'\n  bbb: bbb_2\n  ccc: ccc_1\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\n  model:\n  - task-11-ml_model.bin\noutputResourceAbsolutePath: null\noutputResourceCode: task-12-output-stub-for-predict\nresourceStorageUrls:\n  task-12-output-stub-for-predict: launchpad://\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-11-ml_model.bin: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"2bb8fff0bd8ed9e52060454c9e1a4a9be0018afb138a4419cd725e1d45179966\"}}\'\n  code: simple-metrics.predict:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-predict.py\n  metrics: true\n  params: null\n  type: predict\nworkingPath: null\n',1,1551343508709,1,1551343527658,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.predict_1.1\\\\simple-metrics-predict.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\12\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'20\', \'bbb\': \'bbb_2\', \'ccc\': \'ccc_1\', \'ddd\': \'10\', \'seed\': \'1\'}\n    2019-02-28 00:45:24.733237\n  exitCode: 0\n  isOk: true\n  ok: true\n','error: null\nmetrics: \"values:\\r\\n  sum: 121\\r\\n\"\nstatus: Ok\n',2,4,3,1,1551343528960,2),(13,4,'clean: false\nhyperParams:\n  aaa: \'10\'\n  bbb: bbb_2\n  ccc: ccc_2\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\noutputResourceAbsolutePath: null\noutputResourceCode: task-13-ml_model.bin\nresourceStorageUrls:\n  task-13-ml_model.bin: launchpad://\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"845efccfb2728e7f90ce665027e6aceedf32871892f16982b68d9404f23ccd17\"}}\'\n  code: simple-metrics.fit:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-fit.py\n  metrics: false\n  params: null\n  type: fit\nworkingPath: null\n',1,1551343275818,1,1551343294652,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.fit_1.1\\\\simple-metrics-fit.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\13\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'10\', \'bbb\': \'bbb_2\', \'ccc\': \'ccc_2\', \'ddd\': \'10\', \'seed\': \'1\'}\n    Done.\n    2019-02-28 00:41:33.759331\n  exitCode: 0\n  isOk: true\n  ok: true\n',NULL,1,4,3,1,1551343296074,2),(14,4,'clean: false\nhyperParams:\n  aaa: \'10\'\n  bbb: bbb_2\n  ccc: ccc_2\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\n  model:\n  - task-13-ml_model.bin\noutputResourceAbsolutePath: null\noutputResourceCode: task-14-output-stub-for-predict\nresourceStorageUrls:\n  task-13-ml_model.bin: launchpad://\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-14-output-stub-for-predict: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"2bb8fff0bd8ed9e52060454c9e1a4a9be0018afb138a4419cd725e1d45179966\"}}\'\n  code: simple-metrics.predict:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-predict.py\n  metrics: true\n  params: null\n  type: predict\nworkingPath: null\n',1,1551343539120,1,1551343560917,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.predict_1.1\\\\simple-metrics-predict.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\14\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'10\', \'bbb\': \'bbb_2\', \'ccc\': \'ccc_2\', \'ddd\': \'10\', \'seed\': \'1\'}\n    2019-02-28 00:45:58.672235\n  exitCode: 0\n  isOk: true\n  ok: true\n','error: null\nmetrics: \"values:\\r\\n  sum: 120\\r\\n\"\nstatus: Ok\n',2,4,3,1,1551343559370,2),(15,4,'clean: false\nhyperParams:\n  aaa: \'20\'\n  bbb: bbb_2\n  ccc: ccc_2\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\noutputResourceAbsolutePath: null\noutputResourceCode: task-15-ml_model.bin\nresourceStorageUrls:\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-15-ml_model.bin: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"845efccfb2728e7f90ce665027e6aceedf32871892f16982b68d9404f23ccd17\"}}\'\n  code: simple-metrics.fit:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-fit.py\n  metrics: false\n  params: null\n  type: fit\nworkingPath: null\n',1,1551343306380,1,1551343318909,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.fit_1.1\\\\simple-metrics-fit.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\15\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'20\', \'bbb\': \'bbb_2\', \'ccc\': \'ccc_2\', \'ddd\': \'10\', \'seed\': \'1\'}\n    Done.\n    2019-02-28 00:41:57.292153\n  exitCode: 0\n  isOk: true\n  ok: true\n',NULL,1,4,3,1,1551343326666,2),(16,4,'clean: false\nhyperParams:\n  aaa: \'20\'\n  bbb: bbb_2\n  ccc: ccc_2\n  ddd: \'10\'\n  seed: \'1\'\ninputResourceAbsolutePaths: {\n  }\ninputResourceCodes:\n  feature:\n  - simple-dataset-IRIS_input_output_v2.txt\n  - simple-dataset-IRIS_input_output_v2.txt\n  model:\n  - task-15-ml_model.bin\noutputResourceAbsolutePath: null\noutputResourceCode: task-16-output-stub-for-predict\nresourceStorageUrls:\n  simple-dataset-IRIS_input_output_v2.txt: launchpad://\n  task-16-output-stub-for-predict: launchpad://\n  task-15-ml_model.bin: launchpad://\nsnippet:\n  checksum: \'{\"checksums\":{\"SHA256\":\"2bb8fff0bd8ed9e52060454c9e1a4a9be0018afb138a4419cd725e1d45179966\"}}\'\n  code: simple-metrics.predict:1.1\n  env: python-3\n  fileProvided: false\n  filename: simple-metrics-predict.py\n  metrics: true\n  params: null\n  type: predict\nworkingPath: null\n',1,1551343569507,1,1551343582226,'exec:\n  console: |\n    Using TensorPlan backend.\n    [\'D:\\\\aiai-test\\\\aiai-station\\\\resources\\\\localhost-8888\\\\SNIPPET\\\\simple-metrics.predict_1.1\\\\simple-metrics-predict.py\', \'D:\\\\aiai-test\\\\aiai-station\\\\task\\\\localhost-8888\\\\0\\\\16\\\\artifacts\\\\params.yaml\']\n    {\'aaa\': \'20\', \'bbb\': \'bbb_2\', \'ccc\': \'ccc_2\', \'ddd\': \'10\', \'seed\': \'1\'}\n    2019-02-28 00:46:21.491401\n  exitCode: 0\n  isOk: true\n  ok: true\n','error: null\nmetrics: \"values:\\r\\n  sum: 198\\r\\n\"\nstatus: Ok\n',2,4,3,1,1551343589763,2);
/*!40000 ALTER TABLE `aiai_lp_task` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2019-02-28  0:50:06
