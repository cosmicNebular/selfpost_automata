import org.apache.commons.io.FileUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

def httpClient = new DefaultHttpClient()


def properties = new Properties()
properties.load(getClass().getResourceAsStream("/config.properties"))
def bucket = properties.getProperty("bucket")
def configDirectory = new File(properties.getProperty("configDirectory"))
def projectDirectory = new File(properties.getProperty("projectDirectory"))
def yandexToken = properties.getProperty("yandexToken")
def vmLink = properties.getProperty("vmLink")


def executeShell(String cmd, File directory) {
    def process = new ProcessBuilder(["sh", "-c", cmd])
            .directory(directory)
            .redirectErrorStream(true).start()
    process.outputStream.close()
    def result = process.inputStream.eachLine {
        println it
        it
    }
    result
}

println "------------------------------------------------------------------------------------------------------------------------------------------------------------------------"

if (!configDirectory.exists())
    if (configDirectory.mkdir())
        println "${configDirectory.getName()} was created"
    else
        println 'There is problem with creating config directory'
else println "Directory making skipped, already exists"

def fileNames = ["docker-compose.yaml", "Dockerfile", "values_production.yaml"]

println "------------------------------------------------------------------------------------------------------------------------------------------------------------------------"

for (fileName in fileNames) {
    def file = new File("${configDirectory as String}/$fileName")
    if (file.exists()) {
        file.delete()
    }
    def response = httpClient.execute(new HttpGet("$bucket/$fileName"))
    if (response.getStatusLine().statusCode != 200) {
        println "There is problems with downloading files!"
        System.exit(-1)
    }
    def inputStream = response.getEntity().getContent()
    FileUtils.copyInputStreamToFile(inputStream, file)
    println "Downloaded $fileName"
}

println "------------------------------------------------------------------------------------------------------------------------------------------------------------------------"

for (fileName in fileNames) {
    def file = new File("${configDirectory as String}/$fileName")
    def projectFile = new File("${projectDirectory as String}/$fileName")
    if (fileName == "values_production.yaml") {
        projectFile = new File("${projectDirectory as String}/values/$fileName")
    }
    projectFile.delete()
    println "Deleted $fileName from the project directory"
    projectFile.createNewFile()
    FileUtils.copyFile(file, projectFile)
    println "Copied $fileName to the project directory"
}

println "------------------------------------------------------------------------------------------------------------------------------------------------------------------------"

//def status = executeShell("docker stats", projectDirectory)
//if(status.contains("Cannot connect to the Docker daemon")){
//    executeShell("git -c credential.helper= -c core.quotepath=false -c log.showSignature=false checkout HEAD .", projectDirectory)
//    println "Rollback of changes performed"
//    System.exit(-1)
//}

println "------------------------------------------------------------------------------------------------------------------------------------------------------------------------"

def result = executeShell("docker inspect --type=image selfpost-service_selfpost:latest", projectDirectory)
if (!result.contains("No such image")) {
    executeShell("docker rmi --force selfpost-service_selfpost:latest", projectDirectory)
}
executeShell("docker build -t selfpost-service_selfpost .", projectDirectory)

println "Built the docker image"

println "------------------------------------------------------------------------------------------------------------------------------------------------------------------------"

//executeShell("docker login --username oauth --password $yandexToken cr.yandex", projectDirectory)
executeShell("docker tag selfpost-service_selfpost cr.yandex/crpgs6mg2nanrgolec3a/selfpost-service_selfpost", projectDirectory)
executeShell("docker push cr.yandex/crpgs6mg2nanrgolec3a/selfpost-service_selfpost", projectDirectory)

println "Pushed the docker image"

println "------------------------------------------------------------------------------------------------------------------------------------------------------------------------"


executeShell("ssh $vmLink sudo docker ps -a", projectDirectory)
executeShell("ssh $vmLink docker login --username oauth --password $yandexToken cr.yandex", projectDirectory)
executeShell("ssh $vmLink docker pull cr.yandex/crpgs6mg2nanrgolec3a/selfpost-service_selfpost", projectDirectory)

println "Pulled docker image into the vm"

println "------------------------------------------------------------------------------------------------------------------------------------------------------------------------"

def iamToken = executeShell("yc iam create-token", projectDirectory)
def request = new HttpPost("https://compute.api.cloud.yandex.net/compute/v1/instances/fhmas9gc9jn5t7o35iir:restart")
request.addHeader("Authorization", "Bearer $iamToken")
request.setEntity(new StringEntity("{}"))
def response = httpClient.execute(request)
if (response.getStatusLine().statusCode == 200) {
    println "Restart Successful"
} else {
    println "Problems with restart"
}

println "------------------------------------------------------------------------------------------------------------------------------------------------------------------------"

executeShell("git -c credential.helper= -c core.quotepath=false -c log.showSignature=false checkout HEAD .", projectDirectory)

println "Rollback of changes performed"

printf "Epic Success!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ^_^"
