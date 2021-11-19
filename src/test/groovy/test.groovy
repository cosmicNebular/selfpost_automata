def projectDirectory = new File("/Users/klykovandrey/GolandProjects/selfpost-service/")
def process = new ProcessBuilder(["sh", "-c", "git checkout ."])
        .directory(projectDirectory)
        .redirectErrorStream(true).start()
process.outputStream.close()
process.inputStream.eachLine { println it }
if (!process.waitFor()) {
    System.exit(-1)
}
