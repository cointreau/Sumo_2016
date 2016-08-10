import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;


public class Config {
	private String sumoBin = "C:/Users/WonKyung/git/KCC2016/sumo-0.25.0/bin/sumo-gui.exe";
	private String config = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_sim.cfg";
	private String relEdgesFileDir = "C:/Users/WonKyung/git/KCC2016/DJproject/relatedEdges.txt";
	//		String trafDirectionFileDir = "C:/Users/WonKyung/git/KCC2016/DJproject/trafficDirection.txt";
	private String policyDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_policy_v1.3.xml";
	private String mapVersion = "1.2";
	
	public String getSumoBinDir(){
		return sumoBin;
	}
	
	public String getConfigDir(){
		return config;
	}
	
	public String getRelEdgesFileDir(){
		return relEdgesFileDir;
	}
	
	public String getPolicyDir(){
		return policyDir;
	}
	
	public String getMapVersion(){
		return mapVersion;
	}
}
