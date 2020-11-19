package com.tibco.bw.maven.plugin.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;



@Mojo(name = "bwfabric8json", defaultPhase = LifecyclePhase.INSTALL)
public class BWJsonMojo extends AbstractMojo{


	@Parameter(defaultValue="${project}", readonly=true)
	private MavenProject project;
	
	
	 @Parameter(defaultValue = "${project.projectBuildingRequest.activeProfileIds}", property = "profile", required = false)
	 private List<String> profileIds;
	


	private String getWorkspacepath() {

		String workspacePath= System.getProperty("user.dir");
		String wsPath= workspacePath;
		if(wsPath.indexOf(".parent")!=-1){
			wsPath= workspacePath.substring(0, workspacePath.lastIndexOf(".parent"));
		}

		return wsPath;

	}

	public static void mkDirs(File root, List<String> dirs, int pos) {
		if(dirs!=null){
			if (pos == dirs.size()) return;
			String s=dirs.get(pos);
			File subdir = new File(root, s);
			if(!subdir.exists())
				subdir.mkdir();
			mkDirs(subdir, dirs, pos+1);
		}

	}

	private void copyFile(File source, File destin) throws IOException{
		InputStream in = new FileInputStream(source);

		OutputStream out = new FileOutputStream(destin);



		byte[] buf = new byte[1024];
		int len;

		while ((len = in.read(buf)) > 0) {

			out.write(buf, 0, len);

		}

		in.close();
		out.close();

	}

	
	private Properties getK8sPropertiesFromFile() throws MojoExecutionException{
		String file=(getWorkspacepath() + File.separator + "k8s-dev.properties");
		String profile=null;
		if(profileIds!=null && !profileIds.isEmpty()){
			profile= profileIds.get(0);
		}
		
		if(profile!=null){
			file= (getWorkspacepath() + File.separator + "k8s-"+profile+".properties");
		}
		
		Properties prop = new Properties();
		InputStream input = null;

		try {

			input = new FileInputStream(file);

			// load the Kubernetes properties file
			prop.load(input);
		}
		catch(Exception e)
		{
			throw new MojoExecutionException("Could not load input from "+file+" due to exception: "+e);
		}

		return prop;

	}

	private Properties getDockerPropertiesFromFile() throws MojoExecutionException{


		String fileDocker=(getWorkspacepath() + File.separator + "docker-dev.properties");
		String profile=null;
		if(profileIds!=null && !profileIds.isEmpty()){
			profile=profileIds.get(0);
		}
		if(profile!=null){
			fileDocker= (getWorkspacepath() + File.separator + "docker-"+profile+".properties");
		}
		Properties propsDocker = new Properties();
		FileInputStream input = null;

		try {

			input = new FileInputStream(fileDocker);

			// load the Docker properties file
			propsDocker.load(input);
		}
		catch(Exception e)
		{
			throw new MojoExecutionException("Could not load input from "+fileDocker+" due to exception: "+e);
		}
		return propsDocker;

	}

	private void writeToYamlFile(String location,
			Map<String, Object> data) throws MojoExecutionException {
		//snakeyml for writing maps nested objects to yml file
		DumperOptions options = new DumperOptions(); 
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); 
		Yaml yml = new Yaml(options); 
		FileWriter writer=null;
		try {
			writer = new FileWriter(location);
		} catch (IOException e1) {

			throw new MojoExecutionException("Could not write to service.yml due to exception: "+e1);
		}
		if(writer!=null)
			yml.dump(data, writer);		

	}

	private String getAppVersion() throws MojoExecutionException{
		MavenXpp3Reader reader = new MavenXpp3Reader();
		Model model=null;
		String version=null;
		try {
			model = reader.read(new FileReader(getWorkspacepath()+".parent"+File.separator+"pom.xml"));
		} catch (FileNotFoundException e1) {
			throw new MojoExecutionException("File pom.xml not found for reading application version");
		} catch (IOException e1) {
			throw new MojoExecutionException("Exception while reading pom.xml : "+e1);
		} catch (XmlPullParserException e1) {
			throw new MojoExecutionException("Error while parsing POM file: "+e1);
		}
		if(model!=null){
			version= model.getVersion();

		}
		return version;
	}


	private void createServiceYmlFile(Properties k8sprops) throws MojoExecutionException{

		if(k8sprops.getProperty("fabric8.service") != null && k8sprops.getProperty("fabric8.service").equals("false"))
			return;
		
		
		String locationService = getWorkspacepath() + File.separator + "src/main/fabric8/service.yml";
		File serviceFile = new File(Paths.get(locationService).toString());
		try {
			serviceFile.createNewFile();
		} catch (IOException e1) {
			throw new MojoExecutionException("Could not create file service.yml due to exception: "+e1);
		}
		Map<String, Object> dataService = new HashMap<String, Object>();

		dataService.put("kind", "Service");
		Map<String, Object> metadataService=new HashMap<String, Object>();
		metadataService.put("name", k8sprops.getProperty("fabric8.service.name"));

		Map<String, Object> serviceLabels= new HashMap<String, Object>();
		serviceLabels.put("container", k8sprops.getProperty("fabric8.container.name"));
		serviceLabels.put("project", k8sprops.getProperty("fabric8.label.project"));
		serviceLabels.put("provider","fabric8");
		serviceLabels.put("group", "com.tibco.bw");
		metadataService.put("labels",serviceLabels);
		metadataService.put("namespace",k8sprops.getProperty("fabric8.namespace"));
		dataService.put("metadata", metadataService);
		Map<String, Object> specdataService=new HashMap<String, Object>();
		specdataService.put("type", k8sprops.getProperty("fabric8.service.type"));

		List<Map<String, Object>> portsList=new ArrayList<Map<String, Object>>();
		Map<String, Object> portInfo=new HashMap<String, Object>();
		if(k8sprops.getProperty("fabric8.service.port") != null)
			portInfo.put("port", Integer.parseInt(k8sprops.getProperty("fabric8.service.port")));
		if(k8sprops.getProperty("fabric8.service.containerPort") != null)
			portInfo.put("targetPort", Integer.parseInt(k8sprops.getProperty("fabric8.service.containerPort")));
		if(k8sprops.getProperty("fabric8.service.nodePort") != null)
			portInfo.put("nodePort", Integer.parseInt(k8sprops.getProperty("fabric8.service.nodePort")));
		portsList.add(portInfo);


		Set<Object> portKeys = k8sprops.keySet();
		Map<String, Object> port = null;
		Map<String, String> portName = new HashMap<String, String>(); 
		
		if(portKeys!=null){
			for(Object key: portKeys){
				String keyVal= key.toString();
				if(keyVal!=null) {
					if(Pattern.matches("fabric8.service.(nodePort|port|protocol|containerPort)[.].*", keyVal)) {
						String name = keyVal.split("\\.")[3];
						portName.put(name, name);
					}
					
				}
			}
		}
		
		for (String name : portName.keySet()) {
			port = new HashMap<String, Object>();	
			port.put("name", name);
			port.put("port", Integer.parseInt(k8sprops.getProperty("fabric8.service.port." + name)));
			if(k8sprops.getProperty("fabric8.service.containerPort." + name) != null)
				port.put("targetPort", Integer.parseInt(k8sprops.getProperty("fabric8.service.containerPort." + name)));
			if(k8sprops.getProperty("fabric8.service.nodePort." + name) != null)
				port.put("nodePort", Integer.parseInt(k8sprops.getProperty("fabric8.service.nodePort." + name)));
			
			portsList.add(port);	
		}
		
		specdataService.put("ports", portsList);
		
		Map<String, String> appInfo=new HashMap<String, String>();
		appInfo.put("container", k8sprops.getProperty("fabric8.container.name"));
		appInfo.put("project", k8sprops.getProperty("fabric8.label.project"));
		appInfo.put("provider","fabric8");
		appInfo.put("group", "com.tibco.bw");
		specdataService.put("selector", appInfo);
		dataService.put("spec", specdataService);
		writeToYamlFile(locationService, dataService);		
	}


	private void createDeploymentYmlFile(Properties k8sprops, Properties dockerProps) throws MojoExecutionException{
		String locationDeployment = getWorkspacepath() + File.separator + "src/main/fabric8/deployment.yml";
		File deploymentFile = new File(Paths.get(locationDeployment).toString());
		try {
			deploymentFile.createNewFile();
		} catch (IOException e1) {
			throw new MojoExecutionException("Could not create file deployment.yml due to exception: "+e1);
		}	

		Map<String, Object> data = new HashMap<String, Object>();
		data.put("kind", "Deployment");
		Map<String, Object> metadata=new HashMap<String, Object>();
		metadata.put("name", k8sprops.getProperty("fabric8.replicationController.name"));
		data.put("metadata", metadata);
		Map<String, Object> specdata=new HashMap<String, Object>();
		specdata.put("replicas", Integer.parseInt(k8sprops.getProperty("fabric8.replicas")));
		Map<String, String> appInfoDeployment=new HashMap<String, String>();		
		appInfoDeployment.put("container", k8sprops.getProperty("fabric8.container.name"));
		appInfoDeployment.put("project", k8sprops.getProperty("fabric8.label.project"));
		appInfoDeployment.put("provider","fabric8");
		appInfoDeployment.put("group", "com.tibco.bw");

		specdata.put("selector",appInfoDeployment);
		Map<String,Object> template=new HashMap<String, Object>();
		metadata=new HashMap<String, Object>();
		metadata.put("name", k8sprops.getProperty("fabric8.replicationController.name"));
		Map<String, Object> appInfoLabel=new HashMap<String, Object>();
		appInfoLabel.put("container", k8sprops.getProperty("fabric8.container.name"));
		appInfoLabel.put("project", k8sprops.getProperty("fabric8.label.project"));

		metadata.put("labels", appInfoLabel);
		metadata.put("namespace",k8sprops.getProperty("fabric8.namespace"));
		template.put("metadata", metadata);

		List<Map<String, Object>> containerData=new ArrayList<Map<String,Object>>();
		Map<String, Object> containerInfo= new HashMap<String, Object>();
		containerInfo.put("name", k8sprops.getProperty("fabric8.container.name"));

		containerInfo.put("image", dockerProps.getProperty("docker.image"));

		String version= getAppVersion();
		if(version!=null && version.endsWith("SNAPSHOT")){
			containerInfo.put("imagePullPolicy","Always");
		}


		List<Map<String, Object>> envList=new ArrayList<Map<String, Object>>();
		Set<Object> envKeys = k8sprops.keySet();
		List<String> envVars=new ArrayList<String>();

		if(envKeys!=null){
			for(Object key: envKeys){
				String keyVal= key.toString();
				if(keyVal!=null && keyVal.startsWith("fabric8.env"))
					envVars.add(keyVal);
			}


		}
		if(envVars!=null){
			for(int e=0;e< envVars.size(); e++){
				Map<String , Object> env = new HashMap<String, Object>();	
				String varName= envVars.get(e);
				String envName = varName;
				if(varName!=null && varName.startsWith("fabric8.env"))
					envName= varName.replace("fabric8.env.", "");
				env.put("name", envName);
				env.put("value",k8sprops.getProperty(varName));

				envList.add(env);
			}
		}


		containerInfo.put("env", envList);
		List<Map<String, Object>> portsList = new ArrayList<Map<String, Object>>();
		Map<String, Object> portInfo = new HashMap<String, Object>();
		portInfo.put("containerPort", Integer.parseInt(k8sprops.getProperty("fabric8.service.containerPort")));
		portsList.add(portInfo);
		containerInfo.put("ports", portsList);

		containerData.add(containerInfo);
		Map<String, Object> specdata1=new HashMap<String, Object>();
		specdata1.put("containers", containerData);
		template.put("spec", specdata1);

		specdata.put("template",template);
		data.put("spec", specdata);

		writeToYamlFile(locationDeployment, data);

	}


	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		File root = new File(String.valueOf(getWorkspacepath()));
		List<String> dirs=new ArrayList<String>();
		dirs.add("src");
		dirs.add("main");
		dirs.add("fabric8");
		mkDirs(root, dirs, 0);

		File dstdir = new File(String.valueOf(getWorkspacepath())+File.separator+"src/main/fabric8");
		for(File fileDst: dstdir.listFiles()) {
			if (!fileDst.isDirectory()) 
				fileDst.delete();
		}

		Properties prop= getK8sPropertiesFromFile();

		if(prop.getProperty("fabric8.resources.location")!=null && !prop.getProperty("fabric8.resources.location").isEmpty()){
			//copy the resource files to src/main/fabric8 location
			final File srcdir = new File(prop.getProperty("fabric8.resources.location"));

			if(!srcdir.exists() || !dstdir.exists()){
				throw new MojoExecutionException("Required directories do not exist for loading the resources");
			}



			String[] children = srcdir.list();
			for (int i = 0; i < children.length; i++) {
				try {
					String filename= children[i];
					if(filename!=null && (filename.endsWith(".yml") || filename.endsWith(".yaml")))
						copyFile(new File(srcdir, filename), new File(dstdir,
								filename));
				} catch (IOException e) {
					throw new MojoExecutionException("Could not copy YML files from "+srcdir+" to "+dstdir+" due to exception: "+e);
				}
			}

		}

		else{

			Properties propsDocker= getDockerPropertiesFromFile();			
			createServiceYmlFile(prop);
			createDeploymentYmlFile(prop, propsDocker);			
		}

	}

}
