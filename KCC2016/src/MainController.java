import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.tudresden.sumo.cmd.Edge;
import de.tudresden.sumo.cmd.Junction;
import de.tudresden.sumo.cmd.Lane;
import de.tudresden.sumo.cmd.Route;
import de.tudresden.sumo.cmd.Simulation;
import de.tudresden.sumo.cmd.Trafficlights;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.ws.container.SumoLink;
import de.tudresden.ws.container.SumoLinkList;
import de.tudresden.ws.container.SumoStringList;
import it.polito.appeal.traci.SumoTraciConnection;


public class MainController {

	static String trafficLightSignal[] = {"y", "r", "g"};
	static int changeToSoS = 0;
	static int trafficLightUpdateCycle = 0;

	public static void main(String[] args) throws Exception {
		String sumo_bin = "C:/Users/WonKyung/git/KCC2016/sumo-0.25.0/bin/sumo-gui.exe";
		String config = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_sim.cfg";
		String relEdgesFileDir = "C:/Users/WonKyung/git/KCC2016/DJproject/relatedEdges.txt";
		String trafDirectionFileDir = "C:/Users/WonKyung/git/KCC2016/DJproject/trafficDirection.txt";
		BufferedReader br = new BufferedReader(new FileReader(new File(relEdgesFileDir)));
		String policyDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_policy_v1.0.xml";

		int arrivedCar =0;
		SumoTraciConnection conn = new SumoTraciConnection(sumo_bin, config);
		conn.addOption("step-length", "1");
		conn.runServer();

		//#### initiation of CS-monitoring camera
		String line = "";
		HashMap<String, CS> csList = new HashMap<String, CS>();
		while((line = br.readLine())!=null){
			String[] split = line.split("\\t");
			CS cs = new CS(split[0]);

			for (int i=1; i<split.length; i++){
				cs.addEdge(split[i]);
				cs.initCamera();
			}
			csList.put(cs.getLocation(), cs);
		}

		//#### initiation of CS-traffic light -- traffic light에 의해 control되는 link들을 "순서대로" tlight에 집어넣음
		//순서가 지켜지지 않을 시 tlight가 꼬여서 control하는데 문제가 생김.
		for (Entry<String, CS> cs: csList.entrySet()){
			if (getPassingNodes().contains(cs.getKey()))
				continue;
			//randomly assign the links of traffic lights
			SumoLinkList sll = (SumoLinkList)conn.do_job_get(Trafficlights.getControlledLinks(cs.getKey()));
			for (SumoLink link: sll){
				String linkname = link.from.substring(0, link.from.length()-2) +"@" + link.to.substring(0, link.to.length()-2);
				int rand = new Random().nextInt(3);
				cs.getValue().addTLight(linkname, trafficLightSignal[rand]);
			}
			cs.getValue().initTLight();
		}


		// ####### policy initiation ######
		ArrayList<Policy> policyList = parsingPolicy(policyDir);

		//policy1에 해당하는 edges를 넣는다. 본 list는 policy factor의 location이 edges인 것에 대하여 하나씩 있어야 한다. 
		// 일단 한개만 있으니까 그대로 하고 추가시 코드 수정 필요..  
		List<String> monitoringEdges = new ArrayList<String>();
		for (Policy p: policyList){
			//policy/factor/location/target=edges 인 경우.
			if (p.getFactor().getLocation_target().compareTo("edges")==0){
				for (String edg: p.getFactor().getEdges()){
					monitoringEdges.add(edg);
				}
			}
			if (p.getFactor().getVehicle_target().compareTo("all")==0){
				changeToSoS = p.getFactor().getVehicle_number();
			}
		}


		conn.do_job_set(Vehicle.add("init", "car", "rush1", 0, 0, 13.8, (byte) 0));

		// #### assign traffic light initially
		for (Entry<String, CS> e: csList.entrySet()){
			if (getPassingNodes().contains(e.getKey()))
				continue;

			conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
		}


		int SoSstate = 0;			// 0-- normal state, minus number-- SoS state before control, plus number -- SoS state after control
		int flagedTime = 0;			// state 가 sos로 바뀐 시점을 감지하여 저장
		String ambulanceId = "";
		List<String> ambulanceRoute = null;
		int vehicleIdx = 0;
		// #### 시뮬레이션 시작.
		for (int i=0; i<3600; i++){

			int simtime;
			simtime = (int) conn.do_job_get(Simulation.getCurrentTime());

			//차 랜덤생성(확률)
			int randNum = (int)(Math.random() * 500);
			if (randNum < 350){
				for (int j=0; j<10; j++)
					conn.do_job_set(Vehicle.add("rush1"+vehicleIdx++, "car", "rush1", simtime, 0, 0.0, (byte) 0));
			}
			else if (randNum >= 350 && randNum <400){
				for (int j=0; j<5; j++)
					conn.do_job_set(Vehicle.add("genr"+vehicleIdx++, "car", "genr1", simtime, 0, 0.0, (byte) 0));
			}
			else if (randNum >= 400 && randNum <450){
				for (int j=0; j<5; j++)
					conn.do_job_set(Vehicle.add("genr"+vehicleIdx++, "car", "genr2", simtime, 0, 0.0, (byte) 0));
			}
			else if (randNum >= 450 && randNum <499){
				for (int j=0; j<5; j++)
					conn.do_job_set(Vehicle.add("genr"+vehicleIdx++, "car", "genr3", simtime, 0, 0.0, (byte) 0));
			}
			else if (randNum >= 499 && randNum <500 && SoSstate != 2){
				conn.do_job_set(Vehicle.add("ambul"+vehicleIdx, "ambulance", "ambul1", simtime, 0, 0.0, (byte) 0));
				ambulanceRoute = (List<String>)conn.do_job_get(Vehicle.getRoute("ambul"+vehicleIdx));
				vehicleIdx++;
			}

			//traffic light 주기적 업데이트. normal state.
			if (i%trafficLightUpdateCycle ==0 && SoSstate==0){		//정해진 traffic light update cycle마다
				for (Entry<String, CS> e: csList.entrySet()){
					if (getPassingNodes().contains(e.getKey()))
						continue;
					e.getValue().updateAllTrafficLight(conn, trafficLightSignal);
					conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
				}
			}

			//traffic jam 발생 체크를 위하여 현재 도로의 상황을 카메라를 통해 받아옴
			int nCar=0;
			for (String edg: monitoringEdges){
				for (Entry<String, CS> e: csList.entrySet()){
					if (e.getValue().getEdgeList().contains(edg)){
						nCar += e.getValue().getCamera().get(edg);
						break;
					}
				}
			}
			
			//ambulance가 나타났는지 체크함(policy2의 대비를 하기 위해)
			List<String> vehicles = (List<String>) conn.do_job_get(Vehicle.getIDList());
			if (SoSstate != 2 && SoSstate != -2){
				for (String str: vehicles){
					if (str.startsWith("ambul")){
						ambulanceId = str;
						SoSstate = -2;
					}
				}
			}

			// 만약 ambulance가 도로상에 나타났을 경우, 처리를 위해 현재 시간을 flag로 못박고 SoS state를 policy2로 업데이트 함.
			if (SoSstate == -2){
				System.out.println("-------------------------------------------------Ambulance appears!!");
				flagedTime = i;
				SoSstate = 2;
			}

			//policy2의 적용 -- 기존에 policy1의 발동보다 상위의 priority.
			if (SoSstate == 2){
				//기존의 신호등을 원래대로 돌린 뒤
				
				for (Entry<String, CS> e: csList.entrySet()){
					if (getPassingNodes().contains(e.getKey()))
						continue;

					//사라진 ambulance가 아직 처리되기 전이라면... 
					if (!((List<String>) conn.do_job_get(Vehicle.getIDList())).contains(ambulanceId))
						continue;

					if (i%trafficLightUpdateCycle == 0)
						e.getValue().updateAllTrafficLight(conn, trafficLightSignal);		//기존의 SoSstate==1이었으면 red로 일부 바뀐 신호등이 있기 때문에 이렇게 해주어야함

					String locationAmbulance = (String)conn.do_job_get(Vehicle.getRoadID(ambulanceId));
					String locationAmbulanceTo = getToOfEdge(locationAmbulance);
					
					//만약 현재의 location이 edge가 아닌 junction으로 잡히는 경우(가 존재함, SUMO 상의 오류..) 
					if (locationAmbulanceTo == null){
						break;
					}
					
					ArrayList<String> ambulanceRouteNodes = getNodesFromRoutes(ambulanceRoute);
					
					//현재 ambulance route 상의 CS이며 & ambulance가 위치한 앞 3구간 이내의 CS이면  
					if (ambulanceRouteNodes.contains(e.getKey()) && 
							(ambulanceRouteNodes.indexOf(locationAmbulanceTo) +3 > ambulanceRouteNodes.indexOf(e.getKey())) && 
							(ambulanceRouteNodes.indexOf(locationAmbulanceTo) <= ambulanceRouteNodes.indexOf(e.getKey()))){
						for (String edge: e.getValue().getEdgeList()){
				
//							if (locationAmbulance.compareTo(edge)==0 && locationAmbulanceTo.compareTo(e.getKey())==0){ -- 만약 policy2의 신호를 앰뷸런스가 위치한 노드만으로 한정하고 싶은 경우
							if (ambulanceRoute.contains(edge) && getToOfEdge(edge).compareTo(e.getKey())==0){		// 해당 CS가 포함한 모든 엣지에 대해 이 엣지가 앰뷸런스 루트에 포함된 엣지이며 이 엣지의 to가 현재 CS의 키이면 (이후 조건은 CS의 키 방향으로 진입하는 앰뷸런스 루트 상의 엣지가 if문 안으로 들어가는 것을 막기 위함)
								for (TLight t: e.getValue().gettlightMap()){
									if (t.getKey().startsWith(edge)){
										e.getValue().updateTrafficLight(conn, t.getKey(), policyList.get(1).getOperation().getLight());		//해당 edge에서 빠져나가는 노드는 모두 g로 바꾸어줌.
									}
									else
										e.getValue().updateTrafficLight(conn, t.getKey(), "r");
								}
							}
						}
					conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
					}
				
					//policy2의 신호체계 -- 앰뷸런스의 모든 루트를 전부 g로 바꿈.
/*					if (getNodesFromRoutes((List<String>)conn.do_job_get(Route.getEdges("ambul1"))).contains(e.getKey()))
						e.getValue().updateAllTrafficLightToRed(conn);*/
					
/*					for (String tl: ambulanceRoute){
						for (TLight t: e.getValue().gettlightMap()){
							if (t.getKey().startsWith(tl)){		//만약 이 edge 명으로 시작하면,
								e.getValue().updateTrafficLight(conn, t.getKey(), policyList.get(1).getOperation().getLight());		//해당 edge에서 빠져나가는 노드는 모두 g로 바꾸어줌.
							}
						}
						conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
					}*/
				}
			}

			//policy1의 적용. 우선순위가 가장 낮으므로 어떠한 상황도 아닌 normal 상황에서만 발동하게 된다
			if (nCar >= changeToSoS && SoSstate==0){		//SoS 상황 설정 -- 해당 차로에 100대가 넘는 차량이 몰려있을 경우를 SoS 상황으로 설정함.
				SoSstate = -1;
				flagedTime = i;
			}

			System.out.println((simtime/1000)+" tick / "+nCar +" cars on the road now");

			//SoS 상황시 대응 (policy1)
			if (SoSstate == -1){
				//모든 신호등을 r로 바꾼 뒤, 
				System.out.println("-----------------------------------------------SOS!!!!");
				SumoStringList strList = new SumoStringList(monitoringEdges);

				for (Entry<String, CS> e: csList.entrySet()){
					if (getPassingNodes().contains(e.getKey()))
						continue;

					//해당 길목(rush1)에 해당하는 신호등들의 신호만 g로 바꿈. 이부분 코드 수정 필요? rush1에 해당하는 노드를 하드 코딩 말고 뭔가 다른 방법으로 알아내어야 함
					if (getNodesFromRoutes((List<String>)conn.do_job_get(Route.getEdges("rush1"))).contains(e.getKey())){
						e.getValue().updateAllTrafficLightToRed(conn);

						for (String tl: strList){
							//							for (String key: e.getValue().gettlightMap().keySet()){
							for (TLight t: e.getValue().gettlightMap()){
								if (t.getKey().startsWith(tl)){		//만약 이 edge 명으로 시작하면,
									e.getValue().updateTrafficLight(conn, t.getKey(), policyList.get(1).getOperation().getLight());		//해당 edge에서 빠져나가는 노드는 모두 g로 바꾸어줌.
								}
							}
							conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
						}
					}
				}

				SoSstate = 1;
			}

			//최소 15tick 후, flag를 0으로 바꿔줌.
			int policyKeepingTime=0;
			if (SoSstate > 0)
				policyKeepingTime = policyList.get(SoSstate-1).getOperation().getSustainTime();

			if ((flagedTime +policyKeepingTime < i && SoSstate == 1) || (flagedTime+policyKeepingTime < i && SoSstate == 2)){
				SoSstate = 0;
				flagedTime = 0;
				ambulanceId = "";
				//tlight 정상화
				for (Entry<String, CS> e: csList.entrySet()){
					if (getPassingNodes().contains(e.getKey()))
						continue;
					e.getValue().updateAllTrafficLight(conn, trafficLightSignal);
					conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
				}
				System.out.println("------------------------------------------------Emergency finished");
			}

			//monitoring update
			for (Entry<String, CS> c: csList.entrySet()){
				c.getValue().updateCamera(conn);
			}

			arrivedCar += (int) conn.do_job_get(Simulation.getArrivedNumber());
			conn.do_timestep();

		}

		System.out.println("# Arrived car is " + arrivedCar);

		conn.close();



	}

	
	private static ArrayList<Policy> parsingPolicy(String policyDir) {
		// TODO Auto-generated method stub
		ArrayList<Policy> policyList = null;
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(policyDir));

			//normal_policy가 가지고 있는 non-traffic jam 일 때의 신호등 update cycle을 가장 먼저 초기화함.
			NodeList nList = doc.getElementsByTagName("normal_policy");
			Element el = (Element) nList.item(0);
			trafficLightUpdateCycle = Integer.parseInt(el.getAttribute("updateCycle"));

			nList = doc.getElementsByTagName("policy");
			policyList = new ArrayList<Policy>();
			for (int i=0; i<nList.getLength(); i++){
				Element n = (Element)nList.item(i);
				Policy tmpPol = new Policy(n);
				policyList.add(tmpPol);
			}
		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return policyList;
	}

	//route가 포함하는 노드를 받아옴. 시작 노드와 end 노드는 제외됨.
	private static ArrayList<String> getNodesFromRoutes(List<String> routes){
		String edgeDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_v1.2.edg.xml";
		ArrayList<String> nodes = new ArrayList<String>();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		try {
			db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(edgeDir));
			//일단 노드를 뽑아내서 hashmap에 넣어둠. 
			NodeList nList = doc.getElementsByTagName("edge");
			for (String ru: routes){
				for (int i=0; i<nList.getLength(); i++){
					Element n = (Element)nList.item(i);
					if (n.getAttribute("id").compareTo(ru) == 0)
						nodes.add(n.getAttribute("to"));
				}
			}
			if (nodes.size()>1)
				nodes.remove(nodes.size()-1);

		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return nodes;
	}

	private static ArrayList<String> getPassingNodes(){
		String nodeDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_v1.2.nod.xml";
		ArrayList<String> nodes = new ArrayList<String>();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		try {
			db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(nodeDir));
			//일단 노드를 뽑아내서 hashmap에 넣어둠. 
			NodeList nList = doc.getElementsByTagName("node");

			for (int i=0; i<nList.getLength(); i++){
				Element n = (Element)nList.item(i);
				if(n.getAttribute("type").compareTo("traffic_light")!=0)
					nodes.add(n.getAttribute("id"));
			}

		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return nodes;
	}
	
	private static String getToOfEdge(String edge){
		String edgeDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_v1.2.edg.xml";
		ArrayList<String> nodes = new ArrayList<String>();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		try {
			db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(edgeDir));
			//일단 노드를 뽑아내서 hashmap에 넣어둠. 
			NodeList nList = doc.getElementsByTagName("edge");
			for (int i=0; i<nList.getLength(); i++){
				Element e = (Element)nList.item(i);
				if (e.getAttribute("id").compareTo(edge)==0)
					return e.getAttribute("to");
			}


		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

}
