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
		String sumo_bin = "D:/Coursework/Thesis/sumo-win32-0.25.0/sumo-0.25.0/bin/sumo-gui.exe";
		String config = "D:/Coursework/Thesis/sumo-win32-0.25.0/sumo-0.25.0/KCCPROJECT/sim.cfg";
		String relEdgesFileDir = "C:/Users/WonKyung/git/KCC2016/relatedEdges.txt";
		String trafDirectionFileDir = "C:/Users/WonKyung/git/KCC2016/trafficDirection.txt";
		BufferedReader br = new BufferedReader(new FileReader(new File(relEdgesFileDir)));
		String policyDir = "policy1.xml";

		int arrivedCar =0;
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

		//#### initiation of CS-traffic light
		br = new BufferedReader(new FileReader(new File(trafDirectionFileDir)));
		while((line = br.readLine())!=null){
			String[] split = line.split("\\t");
			CS cs = csList.get(split[0]);

			//randomly assign the traffic light
			for (int i=1; i<split.length; i++){
				int rand = new Random().nextInt(3);
				cs.addTLight(split[i], trafficLightSignal[rand]);
			}
			cs.initTLight();
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

		SumoTraciConnection conn = new SumoTraciConnection(sumo_bin, config);
		conn.addOption("step-length", "1");
		conn.runServer();

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
		// #### 시뮬레이션 시작.
		for (int i=0; i<3600; i++){

			int simtime;
			simtime = (int) conn.do_job_get(Simulation.getCurrentTime());

			//차 랜덤생성(확률)
			int randNum = (int)(Math.random() * 500);
			if (randNum < 350)
				conn.do_job_set(Vehicle.add("rush1"+i, "car", "rush1", simtime, 0, 13.8, (byte) 0));
			else if (randNum >= 350 && randNum <400)
				conn.do_job_set(Vehicle.add("genr"+i, "car", "genr1", simtime, 0, 13.8, (byte) 0));
			else if (randNum >= 400 && randNum <450)
				conn.do_job_set(Vehicle.add("gern"+i, "car", "genr2", simtime, 0, 13.8, (byte) 0));
			else if (randNum >= 450 && randNum <499)
				conn.do_job_set(Vehicle.add("genr"+i, "car", "genr3", simtime, 0, 13.8, (byte) 0));
			else if (randNum >= 499 && randNum <500){
				conn.do_job_set(Vehicle.add("ambul"+i, "ambulance", "ambul1", simtime, 0, 25.0, (byte) 0));
				ambulanceRoute = (List<String>)conn.do_job_get(Vehicle.getRoute("ambul"+i));
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
				flagedTime = i;
				SoSstate = 2;
			}

			/*			List<String> tmproutes = (List<String>) conn.do_job_get(Route.getEdges("rush1"));
			ArrayList<String> passingNodes = getNodesFromRoutes(tmproutes);*/
			//List<String> monitoringEdges = (List<String>) conn.do_job_get(Route.getEdges("rush1"));	
			/*csList.get("12").getCamera().get("A2S") + csList.get("12").getCamera().get("B2E") +
					csList.get("23").getCamera().get("B3S") + csList.get("23").getCamera().get("C3S");*/



			//policy2의 적용 -- 기존에 policy1의 발동보다 상위의 priority.
			if (SoSstate == 2){
				System.out.println("Ambulance appears!!");
				//기존의 신호등을 원래대로 돌린 뒤
				for (Entry<String, CS> e: csList.entrySet()){
					if (getPassingNodes().contains(e.getKey()))
						continue;

					if (i%trafficLightUpdateCycle == 0)
						e.getValue().updateAllTrafficLight(conn, trafficLightSignal);		//기존의 SoSstate==1이었으면 red로 일부 바뀐 신호등이 있기 때문에 이렇게 해주어야함

					/*					if (getNodesFromRoutes((List<String>)conn.do_job_get(Vehicle.getRoute(ambulanceId))).contains(e.getKey())){
						System.out.println(e.getKey());
					}*/
					//policy2의 새로운 신호 체계를 적용해야 함. 일단 그 주변 신호는 모두 빨간색으로 업데이트 한뒤
					//					if (e.getKey().compareTo("12")==0 || e.getKey().compareTo("13")==0 || e.getKey().compareTo("23")==0)
					if (getNodesFromRoutes((List<String>)conn.do_job_get(Route.getEdges("ambul1"))).contains(e.getKey()))
						e.getValue().updateAllTrafficLightToRed(conn);
					for (String tl: ambulanceRoute){
						for (String key: e.getValue().gettlightMap().keySet()){
							if (key.startsWith(tl)){		//만약 이 edge 명으로 시작하면,
								e.getValue().updateTrafficLight(conn, key, policyList.get(1).getOperation().getLight());		//해당 edge에서 빠져나가는 노드는 모두 g로 바꾸어줌.
							}
						}
						conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
					}
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
				System.out.println("SOS!!!!");
				SumoStringList strList = new SumoStringList(monitoringEdges);

				for (Entry<String, CS> e: csList.entrySet()){
					if (getPassingNodes().contains(e.getKey()))
						continue;

					//해당 길목(rush1)에 해당하는 신호등들의 신호만 g로 바꿈. 이부분 코드 수정 필요? rush1에 해당하는 노드를 하드 코딩 말고 뭔가 다른 방법으로 알아내어야 함
					//					if (e.getKey().compareTo("02")==0 || e.getKey().compareTo("12")==0 || e.getKey().compareTo("13")==0 || e.getKey().compareTo("23")==0 || e.getKey().compareTo("33")==0){
					if (getNodesFromRoutes((List<String>)conn.do_job_get(Route.getEdges("rush1"))).contains(e.getKey())){
						e.getValue().updateAllTrafficLightToRed(conn);

						for (String tl: strList){
							for (String key: e.getValue().gettlightMap().keySet()){
								if (key.startsWith(tl)){		//만약 이 edge 명으로 시작하면,
									e.getValue().updateTrafficLight(conn, key, policyList.get(1).getOperation().getLight());		//해당 edge에서 빠져나가는 노드는 모두 g로 바꾸어줌.
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

	private static ArrayList<String> getNodesFromRoutes(List<String> routes){
		String edgeDir = "C:/Users/WonKyung/git/KCC2016/edg.xml";
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
		String nodeDir = "C:/Users/WonKyung/git/KCC2016/nod.xml";
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

}
