@GrabResolver(name='central', root='http://repo1.maven.org/maven2')
@GrabResolver(name='javanet', root='http://download.java.net/maven/2')
@GrabResolver(name='codehaus', root='http://repository.codehaus.org')
@Grab(group='com.amazonaws', module='aws-java-sdk', version='1.3.12')
@Grab(group='commons-logging', module='commons-logging', version='1.1.1')
@Grab(group='commons-codec', module='commons-codec', version='1.6')
@Grab(group='commons-httpclient', module='commons-httpclient', version='3.1')
@Grab(group='javax.mail', module='mail', version='1.4.4')
@Grab(group='org.codehaus.jackson', module='jackson-core-asl', version='1.9.8')
@Grab(group='org.codehaus.jackson', module='jackson-mapper-asl', version='1.9.8')
@Grab(group='stax', module='stax', version='1.2.0')
@Grab(group='stax', module='stax-api', version='1.0.1')

import org.apache.commons.logging.LogFactory

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.PropertiesCredentials

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.Bucket
import com.amazonaws.services.s3.model.StorageClass
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.ProgressEvent
import com.amazonaws.services.s3.model.ProgressListener
import com.amazonaws.services.s3.transfer.Upload
import com.amazonaws.services.s3.transfer.TransferManager

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient
import com.amazonaws.services.elasticbeanstalk.model.S3Location
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest
import com.amazonaws.services.elasticbeanstalk.model.ApplicationVersionDescription
import com.amazonaws.services.s3.model.PutObjectRequest

void upload(appName, bucketName, appDesc, fileToUpload) {
	
	def credentialsFile = new File("credentials.properties")
	if (!credentialsFile.exists()) {
		log(appName, "File '${credentialsFile}' does not exist, you have to have one in the same")
		log(appName, "directory from where you're executing this script. It should have two keys")
		log(appName, "with names 'accessKey' and 'secretKey' with respective content.")
		System.exit(1)
	} else {
		log(appName, "Loading 'credentials.propeties' file")
	}
	
	def credentials = new PropertiesCredentials(credentialsFile)
	log(appName, "Loaded AWS credentials")
	
	def objectKey = "${new Date().format('yyyyMMddHHmmss')}-${fileToUpload.name}"
	def appVersion = "${new Date().format('yyyyMMddHHmmss')}"
	
	// Upload a WAR file to Amazon S3
    def warFile = fileToUpload
	def s3 = new AmazonS3Client(credentials)
    println "Uploading application to Amazon S3"
 	def metadata = new ObjectMetadata()
	metadata.setContentType("application/x-zip")	
	def putObjectRequest = new PutObjectRequest(bucketName, objectKey, warFile)
	putObjectRequest.setMetadata(metadata)
	def s3Result = s3.putObject(putObjectRequest)
    //def s3Result = s3.putObject(bucketName, objectKey, warFile)
    println "Uploaded application $s3Result.versionId"

   def beanstalk = new AWSElasticBeanstalkClient(credentials)
	def applicationVersionRequest = new CreateApplicationVersionRequest(appName, appVersion)
	applicationVersionRequest.setAutoCreateApplication(false)
	applicationVersionRequest.setSourceBundle(new S3Location(bucketName, objectKey))
	applicationVersionRequest.setDescription(appDesc)
	
	println ""
	log(appName, "Creating application version...")
	def applicationVersionResult = beanstalk.createApplicationVersion(applicationVersionRequest)
	def applicationVersionDescription = applicationVersionResult.applicationVersion
    
	//println "Update environment with uploaded application version"
    //def updateEnviromentRequest = new UpdateEnvironmentRequest(environmentName: environmentName, versionLabel: versionLabel)
    //def updateEnviromentResult = elasticBeanstalk.updateEnvironment(updateEnviromentRequest)
    //println "Updated environment $updateEnviromentResult"
	
	log(appName, "Done!")
	log(appName, "App: ${applicationVersionDescription.applicationName}")
	log(appName, "Version: ${applicationVersionDescription.versionLabel}")
	log(appName, "S3 Bucket: ${bucketName}")
	log(appName, "War file:  ${objectKey}")
	log(appName, "Version created at: ${applicationVersionDescription.dateCreated.format('yyyy/MM/dd HH:mm:ss')}")
}

void log(message) {
	println "[${new Date().format('yyyy/MM/dd HH:mm:ss')}] ${message}"
}

void log(returnLine = false, appName, message) {
	
	def msg = "[${new Date().format('yyyy/MM/dd HH:mm:ss')}] [${appName}] ${message}"
	if (returnLine)
		print "\r${msg}"
	else 
		println msg
}

//disable output messages for aws sdk
def logAttribute = "org.apache.commons.logging.Log"
def logValue = "org.apache.commons.logging.impl.NoOpLog"
LogFactory.getFactory().setAttribute(logAttribute, logValue)

//script
if (args.size() != 4) {
	log("Usage: groovy beanstalkUpload.groovy <path_to_war> <s3bucket> <application_name> <application_description>")
	System.exit(1)
}

def fileToUpload = new File(args[0])
def bucketName = args[1]
def appName = args[2].toLowerCase()
def appDesc = args[3]

if (!fileToUpload.exists()) {
	log("File '${fileToUpload}' does not exist")
	System.exit(1)
}

upload(appName, bucketName, appDesc, fileToUpload)
