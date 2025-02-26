package com.tsnsched.core.components;
//TSNsched uses the Z3 theorem solver to generate traffic schedules for Time Sensitive Networking (TSN)
//
//    Copyright (C) 2021  Aellison Cassimiro
//    
//    TSNsched is licensed under the GNU GPL version 3 or later:
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//    
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <https://www.gnu.org/licenses/>.


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.microsoft.z3.*;
import com.tsnsched.core.interface_manager.Printer;
import com.tsnsched.core.network.Network;
import com.tsnsched.core.nodes.Device;
import com.tsnsched.core.nodes.Switch;
import com.tsnsched.core.nodes.TSNSwitch;


/**
 * [Class]: Flow
 * [Usage]: This class specifies a flow (or a stream, in other 
 * words) of packets from one source to one or multiple destinations.
 * It contains references for all the data related to this flow, 
 * including path, timing, packet properties and so on and so forth.
 * The flows can be unicast type or publish subscribe type flows.
 * 
 */

public class Flow implements Serializable {

	// TODO: CHECK FUNCTIONS FOR UNICAST FLOWS

	private Boolean isModifiedOrCreated = false;

	private static final long serialVersionUID = 1L;
	static int instanceCounter = 0;
	private int instance = 0;

	private Printer printer;    
    
	protected String name;
    private int type = 0;
    private int totalNumOfPackets = 0;
    
    private boolean fixedPriority = false;
    private int priorityValue = -1;

	//Specifying the type of the flow:
    public static int UNICAST = 0;
    public static int PUBLISH_SUBSCRIBE = 1;
    
    
    
    private ArrayList<Switch> path;
    private ArrayList<FlowFragment> flowFragments;
    private PathTree pathTree;
    
    protected int pathTreeCount = 0;

    protected transient IntExpr flowPriority; // In the future, priority might be fixed
    protected Device startDevice;
    protected ArrayList<Device> endDeviceList = new ArrayList<Device>();

    private double flowMaximumJitter = -1; 
    private double flowMaximumLatency = -1;
    private double packetSize = -1;

	private double flowFirstSendingTime = -1;
    private double flowSendingPeriodicity = -1;
    

    private transient RealExpr flowFirstSendingTimeZ3;
    private transient RealExpr flowSendingPeriodicityZ3;

    private int numOfPacketsSentInFragment = 0;

    
	/**
     * [Method]: Flow
     * [Usage]: Default constructor method for flow objects.
     * Must be explicit due to call on child class.
     */
    public Flow() {
        
    }
    
    
    /**
     * [Method]: Flow
     * [Usage]: Overloaded constructor method of a flow.
     * Specifies the type of the flow.
     * 
     * @param type      Value specifying the type of the flow (0 - Unicast; 1 - Publish subscribe)
     */
    public Flow(int type) {
        instanceCounter++;
        
        this.instance = instanceCounter;
        this.name = "flow" + Integer.toString(instanceCounter);

        if(type == UNICAST) {
            //Its not a unicast flow
            this.type = 0;
            path = new ArrayList<Switch>();
            flowFragments = new ArrayList<FlowFragment>();
        } else if (type == PUBLISH_SUBSCRIBE) {
            //Its a publish subscribe flow
            this.type = 1;
            pathTree = new PathTree();
        } else {
            instanceCounter--;
            //[TODO]: Throw error
        }
   
    }

    public Flow(String name, int type) {
        instanceCounter++;
        
        this.instance = instanceCounter;
        this.name = name;

        if(type == UNICAST) {
            //Its not a unicast flow
            this.type = 0;
            path = new ArrayList<Switch>();
            flowFragments = new ArrayList<FlowFragment>();
        } else if (type == PUBLISH_SUBSCRIBE) {
            //Its a publish subscribe flow
            this.type = 1;
            pathTree = new PathTree();
        } else {
            instanceCounter--;
            //[TODO]: Throw error
        }
   
    }
    
    /**
     * [Method]: Flow
     * [Usage]: Overloaded constructor method of a flow.
     * Specifies the type of the flow.
     *
     * @param type      Value specifying the type of the flow (0 - Unicast; 1 - Publish subscribe)
     */
    public Flow(int type, double flowFirstSendingTime, double flowSendingPeriodicity) {
        instanceCounter++;
        this.instance = instanceCounter;
        this.name = "flow" + Integer.toString(instanceCounter);

        if(type == UNICAST) {
            //Its not a unicast flow
            this.type = 0;
            path = new ArrayList<Switch>();
            flowFragments = new ArrayList<FlowFragment>();
        } else if (type == PUBLISH_SUBSCRIBE) {
            //Its a publish subscribe flow
            this.type = 1;
            pathTree = new PathTree();
        } else {
            instanceCounter--;
            //[TODO]: Throw error
        }

        this.flowFirstSendingTime = flowFirstSendingTime;
        this.flowSendingPeriodicity = flowSendingPeriodicity;


    }

    public Flow(String name, int type, double flowFirstSendingTime, double flowSendingPeriodicity) {
        instanceCounter++;
        this.instance = instanceCounter;
        this.name = name;

        if(type == UNICAST) {
            //Its not a unicast flow
            this.type = 0;
            path = new ArrayList<Switch>();
            flowFragments = new ArrayList<FlowFragment>();
        } else if (type == PUBLISH_SUBSCRIBE) {
            //Its a publish subscribe flow
            this.type = 1;
            pathTree = new PathTree();
        } else {
            instanceCounter--;
            //[TODO]: Throw error
        }

        this.flowFirstSendingTime = flowFirstSendingTime;
        this.flowSendingPeriodicity = flowSendingPeriodicity;


    }

	/**
     * [Method]: addToPath
     * [Usage]: Adds a switch to the path of switches of the flow
     * 
     * @param swt   Switch to be added to the list
     */
    public void addToPath(TSNSwitch swt) {
        path.add(swt);
    }
    
    public void addToPath(Object source, Object destination) {
    	
    	if(this.pathTree == null) {
    		this.pathTree = new PathTree();
    		PathNode pathNode;
    		pathNode = this.pathTree.addRoot((Device) source);
    		pathNode = pathNode.addChild((TSNSwitch) destination);
    	} else {
    		String nameOfSource = (source instanceof Device? ((Device) source).getName(): ((TSNSwitch) source).getName());
    		
    		PathNode pathNode = pathTree.searchNode(nameOfSource, pathTree.getRoot());
    		
    		if(pathNode == null) {
    			this.printer.printIfLoggingIsEnabled("[ERROR] SPECIFIED SOURCE NODE " + nameOfSource + " NOT FOUND IN TREE OF FLOW");
    		} else {
    			pathNode.addChild(destination);
    		}	
    	}
    	
    }
    
    /**
     * [Method]: convertUnicastFlows
     * [Usage]: Most of the unicast functionalities are not supported anymore.
     * At the beginning of the scheduling process, convert the to a multicast
     * structure without a branching path.
     * 
     */
    
    public void convertUnicastFlow() {
        // AVOID USING THE ARRAY LIST
        // TODO: REMOVE OPTION TO DISTINGUISH BETWEEN UNICAST AND MULTICAST LATER
        if(this.type == UNICAST) {
            LinkedList<PathNode> nodeList;
            
            if(this.pathTree==null) {
            	
	            PathTree pathTree = new PathTree();
	            PathNode pathNode;
	            pathNode = pathTree.addRoot(this.startDevice);            
	            pathNode = pathNode.addChild(path.get(0));
	            nodeList = new LinkedList<PathNode>();
	            nodeList.add(pathNode);
	            for(int i = 1;  i < path.size(); i++) {
	                nodeList.add(nodeList.removeFirst().addChild(path.get(i)));
	            }
	            nodeList.getFirst().addChild(this.endDeviceList.get(0));
	            nodeList.removeFirst();
	            this.setPathTree(pathTree);
	            
            }

            this.type = PUBLISH_SUBSCRIBE;
        }
    }
    
    
    /**
     * [Method]: toZ3
     * [Usage]: After setting all the numeric input values of the class,
     * generates the z3 equivalent of these values and creates any extra
     * variable needed.
     * 
     * @param ctx      Context variable containing the z3 environment used
     */
    public void toZ3(Context ctx) {

        if(this.type == UNICAST) { // If flow is unicast
            // Convert start device to z3
            startDevice.toZ3(ctx);
            
            /*
             * Iterate over the switches in the path. For each switch, 
             * a flow fragment will be created.
             */
            
            int currentSwitchIndex = 0;
            for (Switch swt : this.path) {
                this.pathToZ3(ctx, swt, currentSwitchIndex);
                currentSwitchIndex++;
            }
            
        } else if (this.type == PUBLISH_SUBSCRIBE) { // If flow is publish subscribe
            /*
             * Converts the properties of the root to z3 and traverse the tree
             * doing the same and creating flow fragments for every stream
             * going out of a switch.
             */

            this.startDevice = (Device) this.pathTree.getRoot().getNode();
            this.startDevice.toZ3(ctx);


            
            if(this.priorityValue < 0 || this.priorityValue > 7) {
            	this.flowPriority = ctx.mkIntConst(this.name + "Priority");
            } else {
            	this.flowPriority = ctx.mkInt(this.priorityValue);
            }
            
            this.flowFirstSendingTimeZ3 = ctx.mkRealConst("flow" + this.instance + "FirstSendingTime");

            this.flowSendingPeriodicityZ3 = ctx.mkReal(Double.toString(this.flowSendingPeriodicity));

         
            //this.printer.printIfLoggingIsEnabled("On flow " + this.name + " - " + this.flowSendingPeriodicityZ3 + "; " + this.flowFirstSendingTimeZ3 );

            this.nodeToZ3(ctx, this.pathTree.getRoot(), null);
            
        }
       	
    }
    
    /**
     * [Method]: nodeToZ3
     * [Usage]: Given a node of a tree, the method iterate over its children.
     * For each grand-child, a flow fragment is created. This represents the
     * departure time from the current node, the arrival time in the child and
     * the scheduled time (departure time for grand-child).
     *
     * @param ctx       Context variable containing the z3 environment used
     * @param node      A node of the pathTree
     */
    public FlowFragment nodeToZ3(Context ctx, PathNode node, FlowFragment frag) {
        FlowFragment flowFrag = null;
        int numberOfPackets = Network.PACKETUPPERBOUNDRANGE;

        /*
        this.printer.printIfLoggingIsEnabled("On node " +
                (node.getNode() instanceof Device ?
                ((Device) node.getNode()).getName() :
                ((TSNSwitch) node.getNode()).getName())
        );
        */

        // If, by chance, the given node has no child, then its a leaf
        if(node.getChildren().size() == 0) {
            //this.printer.printIfLoggingIsEnabled("On flow " + this.name + " leaving on node " + ((Device) node.getNode()).getName());
            return flowFrag;
        }

        // Iterate over node's children
        for(PathNode auxN : node.getChildren()) {

            // If child is a device, then its a leaf. Do nothing
            /*
            if(auxN.getNode() instanceof Device) {
                this.printer.printIfLoggingIsEnabled("On flow " + this.name + " leaving on node " + ((Device) auxN.getNode()).getName());
                continue;
            }
            */

            // For each grand children of the current child node
            for(PathNode n : auxN.getChildren()) {

                // Create a new flow fragment
                flowFrag = new FlowFragment(this);

                // Setting next hop
                if(n.getNode() instanceof TSNSwitch) {
                    flowFrag.setNextHop(
                            ((TSNSwitch) n.getNode()).getName()
                    );
                } else {
                    flowFrag.setNextHop(
                            ((Device) n.getNode()).getName()
                    );
                }

                if(((TSNSwitch)auxN.getNode()).getPortOf(flowFrag.getNextHop()).checkIfAutomatedApplicationPeriod()) {
                    numberOfPackets = (int) (((TSNSwitch)auxN.getNode()).getPortOf(flowFrag.getNextHop()).getDefinedHyperCycleSize()/this.flowSendingPeriodicity);
                    flowFrag.setNumOfPacketsSent(numberOfPackets);
                }

                if(auxN.getParent().getParent() == null) { //First flow fragment, fragment first departure = device's first departure
                    flowFrag.setNodeName(((Switch)auxN.getNode()).getName());

                    for (int i = 0; i < numberOfPackets; i++) {
                        /*
                        flowFrag.setDepartureTimeZ3(
                            this.startDevice.getFirstT1TimeZ3(),
                            i
                        );
                        */

                        /**/
                        flowFrag.addDepartureTimeZ3(
                                (RealExpr) ctx.mkAdd(
                                        this.flowFirstSendingTimeZ3,
                                        ctx.mkReal(Double.toString(this.flowSendingPeriodicity * i))
                                )
                        );
                        /**/
                    }


                } else { // Fragment first departure = last fragment scheduled time

                    for (int i = 0; i < numberOfPackets; i++) {

                        flowFrag.addDepartureTimeZ3( // Flow fragment link constraint
                                ((TSNSwitch) auxN.getParent().getNode())
                                        .scheduledTime(
                                                ctx,
                                                i,
                                                auxN.getParent().getFlowFragments().get(auxN.getParent().getChildren().indexOf(auxN))
                                        )
                        );

                    }

                    flowFrag.setNodeName(((TSNSwitch) auxN.getNode()).getName());

                }


                // Setting z3 properties of the flow fragment
                if(this.fixedPriority) {
                    flowFrag.setFragmentPriorityZ3(this.flowPriority); // FIXED PRIORITY (Fixed priority per flow constraint)
                } else {
                    flowFrag.setFragmentPriorityZ3(ctx.mkIntConst(flowFrag.getName() + "Priority"));
                }

                int portIndex = ((TSNSwitch) auxN.getNode()).getConnectsTo().indexOf(flowFrag.getNextHop());
                flowFrag.setPort(
                        ((TSNSwitch) auxN.getNode())
                                .getPorts().get(portIndex)
                );

                for (int i = 0; i<flowFrag.getNumOfPacketsSent(); i++){
                    flowFrag.addScheduledTimeZ3(
                            flowFrag.getPort().scheduledTime(ctx, i, flowFrag)
                    );
                }

                flowFrag.setPacketPeriodicityZ3(this.flowSendingPeriodicityZ3);
                flowFrag.setPacketSizeZ3(ctx.mkReal(Double.toString(this.packetSize)));
                flowFrag.setStartDevice(this.startDevice);
                flowFrag.setReferenceToNode(auxN);

                //Adding fragment to the fragment list and to the switch's fragment list
                auxN.addFlowFragment(flowFrag);
                ((TSNSwitch)auxN.getNode()).addToFragmentList(flowFrag);
                // this.printer.printIfLoggingIsEnabled("Adding fragment to switch " + ((TSNSwitch)auxN.getNode()).getName() + " has " + auxN.getChildren().size() + " children");

            }

            if(flowFrag == null){
                continue;
            }

            if(frag != null && flowFrag.getPreviousFragment() == null) {
                flowFrag.setPreviousFragment(frag);
            }

            // Recursively repeats process to children
            // this.printer.printIfLoggingIsEnabled("Calling node: " + (auxN.getNode() instanceof Device ? ((Device) auxN.getNode()).getName() : ((TSNSwitch) auxN.getNode()).getName()));
            FlowFragment nextFragment = this.nodeToZ3(ctx, auxN, flowFrag);


            if(nextFragment != null) {
                flowFrag.addToNextFragments(nextFragment);
            }
        }

        return flowFrag;
    }




    
    /**
     * [Method]: pathToZ3
     * [Usage]: On a unicast flow, the path is a simple ArrayList.
     * Each switch in the path will be given as a parameter for this function
     * so a flow fragment for each hop on the path can be created.
     * 
     * @param ctx                   Context variable containing the z3 environment used
     * @param swt                   Switch of the current flow fragment
     * @param currentSwitchIndex    Index of the current switch in the path on the iteration
     */
    public void pathToZ3(Context ctx, Switch swt, int currentSwitchIndex) {
        // Flow fragment is created
        FlowFragment flowFrag = new FlowFragment(this);
        
        /*
         * If this flow fragment is the same on the fragment list, then
         * this fragment departure time = source device departure time. Else,
         * this fragment departure time = last fragment scheduled time.
         */
        if(flowFragments.size() == 0) { 
            // If no flowFragment has been added to the path, flowPriority is null, so initiate it
            //flowFrag.setNodeName(this.startDevice.getName());
            for (int i = 0; i < Network.PACKETUPPERBOUNDRANGE; i++) {
                flowFrag.addDepartureTimeZ3( // Packet departure constraint
                    (RealExpr) ctx.mkAdd(
                        this.flowFirstSendingTimeZ3,
                        ctx.mkReal(Double.toString(this.flowSendingPeriodicity * i))
                    )
                );
            }
        } else { 
            for (int i = 0; i < Network.PACKETUPPERBOUNDRANGE; i++) {
                flowFrag.addDepartureTimeZ3(
                    ((TSNSwitch) path.get(currentSwitchIndex - 1)).scheduledTime(ctx, i, flowFragments.get(flowFragments.size() - 1))
                );
            }
        } 
        flowFrag.setNodeName(((TSNSwitch) path.get(currentSwitchIndex)).getName());            
        
        // Setting extra flow properties
        flowFrag.setFragmentPriorityZ3(ctx.mkIntConst(flowFrag.getName() + "Priority"));
        flowFrag.setPacketPeriodicityZ3(this.flowSendingPeriodicityZ3);
        flowFrag.setPacketSizeZ3(ctx.mkReal(Double.toString(this.packetSize)));

        /*
         * If index of current switch = last switch in the path, then 
         * next hop will be to the end device, else, next hop will be to
         * the next switch in the path.
         */
        
        if((path.size() - 1) == currentSwitchIndex) {
        	flowFrag.setNextHop(this.endDeviceList.get(0).getName());
        } else {
            flowFrag.setNextHop(
                path.get(currentSwitchIndex + 1).getName()
            );
        }
        
        /*
         * The newly created fragment is added to both the switch 
         * (on the list of fragments that go through it) and to 
         * the flow fragment list of this flow.
         */
        
        ((TSNSwitch)swt).addToFragmentList(flowFrag);
        flowFragments.add(flowFrag);
    }


    public void bindToNextFragment(Solver solver, Context ctx, FlowFragment frag){
        if(frag.getNextFragments().size() > 0){

            for(FlowFragment childFrag : frag.getNextFragments()){

                for (int i = 0; i < this.numOfPacketsSentInFragment; i++){
//                    this.printer.printIfLoggingIsEnabled("On fragment " + frag.getName() + " making " + frag.getPort().scheduledTime(ctx, i, frag) + " = " + childFrag.getPort().departureTime(ctx, i, childFrag) + " that leads to " + childFrag.getPort().scheduledTime(ctx, i, childFrag)
//                            + " on cycle of port " + frag.getPort().getCycle().getFirstCycleStartZ3());
                    solver.add(
                            ctx.mkEq(
                                    frag.getPort().scheduledTime(ctx, i, frag),
                                    childFrag.getPort().departureTime(ctx, i, childFrag)
                            )
                    );
                }

                this.bindToNextFragment(solver, ctx, childFrag);

            }

        }
    }


    public void bindAllFragments(Solver solver, Context ctx){
        for(PathNode node : this.pathTree.getRoot().getChildren()){
            for(FlowFragment frag : node.getFlowFragments()){
                this.bindToNextFragment(solver, ctx, frag);
            }
        }
    }

    
    
    public void assertFirstSendingTime(Solver solver, Context ctx) {

        double firstPortSpeed = ((TSNSwitch) this.pathTree
                                    .getRoot()
                                    .getChildren()
                                    .get(0)
                                    .getNode())
                                    .getPortOf(this.getStartDevice().getName())
                                    .getPortSpeed();

        RealExpr firstPortCycleStart = ((TSNSwitch) this.pathTree
                .getRoot()
                .getChildren()
                .get(0)
                .getNode())
                .getPortOf(this.getStartDevice().getName())
                .getCycle()
                .getFirstCycleStartZ3();

        double firstPortCycleDuration = getFirstHopCycleDuration();

        if(this.getPacketSize()/firstPortSpeed >= this.flowFirstSendingTime && (this.flowFirstSendingTime>=0)){
            this.printer.printIfLoggingIsEnabled("Alert: First packet of flow " + this.name + " must have enough time to leave source. Making first sending time a variable.");
            this.flowFirstSendingTime = -1;
        }


        if(this.flowFirstSendingTime >= 0){
            this.printer.printIfLoggingIsEnabled("Alert: " + this.name + " Assert first sending time to " + this.flowFirstSendingTime);

            solver.add(
                ctx.mkEq(
                    this.flowFirstSendingTimeZ3,
                    ctx.mkReal(Double.toString(this.flowFirstSendingTime))
                )
            );

            return;
        }

        /**/
        //this.printer.printIfLoggingIsEnabled(
        solver.add(
            ctx.mkGe(
                this.flowFirstSendingTimeZ3,
                ctx.mkReal(Double.toString(this.getPacketSize()/firstPortSpeed))
                //ctx.mkReal(Double.toString(0))
            )
        );
        /**/

        /**/
        //this.printer.printIfLoggingIsEnabled(
        solver.add(
            ctx.mkLe(
                this.flowFirstSendingTimeZ3,
                ctx.mkAdd(
                    firstPortCycleStart,
                    ctx.mkReal(Double.toString(firstPortCycleDuration))
                )
            )
        );
        /**/


    }

    public double getFirstHopCycleDuration(){
        PathNode firstNode = this.pathTree
                .getRoot()
                .getChildren()
                .get(0);

        double cycleDuration = ((TSNSwitch) firstNode
                .getNode())
                .getPortOf(this.getStartDevice().getName())
                .getCycle()
                .getCycleDuration();

        if(cycleDuration == 0){

            for(FlowFragment frag : firstNode.getFlowFragments()){
                double currentCycleDuration = frag.getPort().getCycle().getCycleDuration();
                if(cycleDuration == 0 || cycleDuration > currentCycleDuration){
                    cycleDuration = currentCycleDuration;
                }
            }

        }
        return cycleDuration;

    }


    /**
     * [Method]: getFlowFromRootToNode
     * [Usage]: Given an end device of a publish subscriber flow, or in other
     * words, a leaf in the pathTree, returns the flow fragments used to go from
     * the root to the leaf.
     * 
     * @param endDevice     End device (leaf) of the desired path
     * @return              ArrayList of flow fragments containing every flow fragment from source to destination
     */
    public ArrayList<FlowFragment> getFlowFromRootToNode(Device endDevice){
        ArrayList<FlowFragment> flowFragments = new ArrayList<FlowFragment>();
        ArrayList<Device> flowEndDevices = new ArrayList<Device>();
        PathNode auxNode = null;
        
        
        // Iterate over leaves, get reference to the leaf of end device
        for(PathNode node : this.pathTree.getLeaves()) {
            flowEndDevices.add((Device) node.getNode());
            
            if((node.getNode() instanceof Device) &&
               ((Device) node.getNode()).getName().equals(endDevice.getName())) {
                auxNode = node;
            }
        } 
        
        // If no leaf contains the desired end device, throw error returns null
        if(!flowEndDevices.contains(endDevice)) {
            // TODO [Priority: Low]: Throw error
            return null;
        }
        
        // Goes from parent to parent adding flowFragments to the list
        while(auxNode.getParent().getParent() != null) {
            flowFragments.add(
                auxNode.getParent().getFlowFragments().get(
                    auxNode.getParent().getChildren().indexOf(auxNode)
                )
            );
            
            auxNode = auxNode.getParent();
        }
        
        /*
         * Since the fragments were added from end device to start device,
         * reverse array list.
         */
        
        Collections.reverse(flowFragments);
        
        return flowFragments;
    }
    
    /**
     * [Method]: getNodesFromRootToNode
     * [Usage]: Given an end device of a publish subscriber flow, or in other
     * words, a leaf in the pathTree, returns the nodes of the path used to go from
     * the root to the leaf.
     * 
     * @param endDevice     End device (leaf) of the desired path
     * @return              ArrayList of nodes containing every node from source to destination
     */
    public ArrayList<PathNode> getNodesFromRootToNode(Device endDevice){
        ArrayList<PathNode> pathNodes = new ArrayList<PathNode>();
        ArrayList<Device> flowEndDevices = new ArrayList<Device>();
        PathNode auxNode = null;
        
        // Iterate over leaves, get reference to the leaf of end device
        for(PathNode node : this.pathTree.getLeaves()) {
            flowEndDevices.add((Device) node.getNode());
            if((node.getNode() instanceof Device) &&
               ((Device) node.getNode()).getName().equals(endDevice.getName())) {
                auxNode = node;
            }
        } 
        
        // If no leaf contains the desired end device, throw error returns null
        if(!flowEndDevices.contains(endDevice)) {
            // TODO [Priority: Low]: Throw error
            return null;
        }
        
        // Goes from parent to parent adding nodes to the list
        while(auxNode != null) {
            pathNodes.add(auxNode);
            
            auxNode = auxNode.getParent();
        }
        
        /*
         * Since the nodes were added from end device to start device,
         * reverse array list.
         */
        
        Collections.reverse(pathNodes);
        
        return pathNodes;
    }
    
    
    /**
     * [Method]: getDepartureTime
     * [Usage]: On a unicast flow, returns the departure time
     * of a certain packet in a certain hop specified by the 
     * parameters.
     * 
     * @param hop           Number of the hop of the packet from the flow
     * @param packetNum     Number of the packet sent by the flow
     * @return              Departure time of the specific packet
     */
    public double getDepartureTime(int hop, int packetNum) {
        double time;
        
        
        time = this.getFlowFragments().get(hop).getDepartureTime(packetNum);
        
        return time;
    }
    
    
    /**
     * [Method]: setUpPeriods
     * [Usage]: Iterate over the switches in the path of the flow. 
     * Adds its periodicity to the periodicity list in the switch.
     * This will be used for the automated application cycles.
     */
    
    public void setUpPeriods(PathNode node) {
    	if(node.getChildren().isEmpty()) {
			return;
    	} else if (node.getNode() instanceof Device) {
    		for(PathNode child : node.getChildren()) {
    			this.setUpPeriods(child);
    		}
    	} else {
    		TSNSwitch swt = (TSNSwitch) node.getNode(); //no good. Need the port
    		Port port = null;
    		
    		for(PathNode child : node.getChildren()) {
    			if(child.getNode() instanceof Device) {
        			port = swt.getPortOf(((Device) child.getNode()).getName());    	
    				this.setUpPeriods(child);			
    			} else if (child.getNode() instanceof TSNSwitch) {
        			port = swt.getPortOf(((TSNSwitch) child.getNode()).getName());  
    				this.setUpPeriods(child);
    			} else {
    				this.printer.printIfLoggingIsEnabled("Unrecognized node");
    				return;
    			}
    			
    			if(!port.getListOfPeriods().contains(this.flowSendingPeriodicity)) {
    				port.addToListOfPeriods(this.flowSendingPeriodicity);
                }
    		}
    		
    	}
    }
    
    
    /**
     * [Method]: getDepartureTime
     * [Usage]: On a publish subscribe flow, returns the departure time
     * of a certain packet in a certain hop that reaches a certain device.
     * The specifications of the packet and destination are given as 
     * parameters.
     * 
     * @param deviceName    Name of the desired target device
     * @param hop           Number of the hop of the packet from the flow
     * @param packetNum     Number of the packet sent by the flow
     * @return              Departure time of the specific packet
     */
    public double getDepartureTime(String deviceName, int hop, int packetNum) {
        double time;
        Device targetDevice = null;
        ArrayList<FlowFragment> auxFlowFragments;
        
        for(Object node : this.pathTree.getLeaves()) {
            if(node instanceof Device) {
                if(((Device) node).getName().equals(deviceName)) {
                    targetDevice = (Device) node;
                }
            }
            
        }
        
        if(targetDevice == null) {
            //TODO: Throw error
        }
        
        auxFlowFragments = this.getFlowFromRootToNode(targetDevice);
        
        time = auxFlowFragments.get(hop).getDepartureTime(packetNum);
        
        return time;
    }
    
    /**
     * [Method]: getDepartureTime
     * [Usage]: On a publish subscribe flow, returns the departure time
     * of a certain packet in a certain hop that reaches a certain device. 
     * The specifications of the packet and destination are given as 
     * parameters.
     * 
     * @param targetDevice  Object containing the desired end device
     * @param hop           Number of the hop of the packet from the flow
     * @param packetNum     Number of the packet sent by the flow
     * @return              Departure time of the specific packet
     */
    public double getDepartureTime(Device targetDevice, int hop, int packetNum) {
        double time;
        ArrayList<FlowFragment> auxFlowFragments;
        
        if(!this.pathTree.getLeaves().contains(targetDevice)) {
            //TODO: Throw error
        }
        
        auxFlowFragments = this.getFlowFromRootToNode(targetDevice);
        
        time = auxFlowFragments.get(hop).getDepartureTime(packetNum);
        
        return time;
    }
    
    
    /**
     * [Method]: getArrivalTime
     * [Usage]: On a unicast flow, returns the arrival time
     * of a certain packet in a certain hop specified by the 
     * parameters.
     * 
     * @param hop           Number of the hop of the packet from the flow
     * @param packetNum     Number of the packet sent by the flow
     * @return              Arrival time of the specific packet
     */
    public double getArrivalTime(int hop, int packetNum) {
        double time;
        
        time = this.getFlowFragments().get(hop).getArrivalTime(packetNum);
        
        return time;
    }
    
    
    /**
     * [Method]: getArrivalTime
     * [Usage]: On a publish subscribe flow, returns the arrival time
     * of a certain packet in a certain hop that reaches a certain
     * device. The specifications of the packet and destination are 
     * given as parameters.
     * 
     * @param deviceName    Name of the desired target device
     * @param hop           Number of the hop of the packet from the flow
     * @param packetNum     Number of the packet sent by the flow
     * @return              Arrival time of the specific packet
     */
    public double getArrivalTime(String deviceName, int hop, int packetNum) {
        double time;
        Device targetDevice = null;
        ArrayList<FlowFragment> auxFlowFragments;
        
        for(Object node : this.pathTree.getLeaves()) {
            if(node instanceof Device) {
                if(((Device) node).getName().equals(deviceName)) {
                    targetDevice = (Device) node;
                }
            }
            
        }
        
        if(targetDevice == null) {
            //TODO: Throw error
        }
        
        auxFlowFragments = this.getFlowFromRootToNode(targetDevice);
        
        time = auxFlowFragments.get(hop).getArrivalTime(packetNum);
        
        return time;
    }
    
    
    /**
     * [Method]: getArrivalTime
     * [Usage]: On a publish subscribe flow, returns the arrival time
     * of a certain packet in a certain hop that reaches a certain device. 
     * The specifications of the packet and destination are given as 
     * parameters.
     * 
     * @param targetDevice  Object containing the desired end device
     * @param hop           Number of the hop of the packet from the flow
     * @param packetNum     Number of the packet sent by the flow
     * @return              Arrival time of the specific packet
     */
    public double getArrivalTime(Device targetDevice, int hop, int packetNum) {
        double time;
        ArrayList<FlowFragment> auxFlowFragments;
        
        if(!this.pathTree.getLeaves().contains(targetDevice)) {
            //TODO: Throw error
        }
        
        auxFlowFragments = this.getFlowFromRootToNode(targetDevice);
        
        time = auxFlowFragments.get(hop).getArrivalTime(packetNum);
        
        return time;
    }
    
    /**
     * [Method]: getScheduledTime
     * [Usage]: On a unicast flow, returns the scheduled time
     * of a certain packet in a certain hop specified by the 
     * parameters.
     * 
     * @param hop           Number of the hop of the packet from the flow
     * @param packetNum     Number of the packet sent by the flow
     * @return              Scheduled time of the specific packet
     */
    public double getScheduledTime(int hop, int packetNum) {
        double time;
        
        time = this.getFlowFragments().get(hop).getScheduledTime(packetNum);
        
        return time;
    }
    
    
    /**
     * [Method]: getScheduledTime
     * [Usage]: On a publish subscribe flow, returns the scheduled time
     * of a certain packet in a certain hop that reaches a certain
     * device. The specifications of the packet and destination are 
     * given as parameters.
     * 
     * @param deviceName    Name of the desired target device
     * @param hop           Number of the hop of the packet from the flow
     * @param packetNum     Number of the packet sent by the flow
     * @return              Scheduled time of the specific packet
     */
    public double getScheduledTime(String deviceName, int hop, int packetNum) {
        double time;
        Device targetDevice = null;
        ArrayList<FlowFragment> auxFlowFragments;
        
        for(Object node : this.pathTree.getLeaves()) {
            if(node instanceof Device) {
                if(((Device) node).getName().equals(deviceName)) {
                    targetDevice = (Device) node;
                }
            }
            
        }
        
        if(targetDevice == null) {
            //TODO: Throw error
        }
        
        auxFlowFragments = this.getFlowFromRootToNode(targetDevice);
        
        time = auxFlowFragments.get(hop).getScheduledTime(packetNum);
        
        return time;
    }
    
    /**
     * [Method]: getScheduledTime
     * [Usage]: On a publish subscribe flow, returns the scheduled time
     * of a certain packet in a certain hop that reaches a certain device. 
     * The specifications of the packet and destination are given as 
     * parameters.
     * 
     * @param targetDevice  Object containing the desired end device
     * @param hop           Number of the hop of the packet from the flow
     * @param packetNum     Number of the packet sent by the flow
     * @return              Scheduled time of the specific packet
     */
    public double getScheduledTime(Device targetDevice, int hop, int packetNum) {
        double time;
        ArrayList<FlowFragment> auxFlowFragments;
        
        if(!this.pathTree.getLeaves().contains(targetDevice)) {
            //TODO: Throw error
        }
        
        auxFlowFragments = this.getFlowFromRootToNode(targetDevice);
        
        time = auxFlowFragments.get(hop).getScheduledTime(packetNum);
        
        return time;
    }
    
    /**
     * [Method]: getAverageLatency
     * [Usage]: Returns the average latency from this flow.
     * On a unicast flow, gets the last scheduled time of 
     * every packet and subtracts by the first departure
     * time of same packet, then divides by the quantity
     * of packets. A similar process is done with the pub-
     * sub flows, the difference is that the flow is broken
     * into multiple unicast flows to repeat the previously
     * mentioned process.
     * 
     * @return          Average latency of the flow
     */
    public double getAverageLatency() {
        double averageLatency = 0;
        double auxAverageLatency = 0;
        int timeListSize = 0;
        Device endDevice = null;

        double firstTransmissionDelay = ((double)this.getPacketSize())/this.getFirstPortSpeed();
        
        if (type == UNICAST) {
            timeListSize = this.getTimeListSize();
            for(int i = 0; i < timeListSize; i++) {
                averageLatency += 
                        this.getScheduledTime(this.flowFragments.size() - 1, i) -
                        this.getDepartureTime(0, i) + 
                        firstTransmissionDelay;
            }
            
            averageLatency /= (timeListSize);
            
        } else if(type == PUBLISH_SUBSCRIBE) {
            
            for(PathNode node : this.pathTree.getLeaves()) {
                timeListSize = this.pathTree.getRoot().getChildren().get(0).getFlowFragments().get(0).getArrivalTimeList().size();;
                endDevice = (Device) node.getNode();
                auxAverageLatency = 0;
                
                for(int i = 0; i < timeListSize; i++) {
                    auxAverageLatency += 
                            this.getScheduledTime(endDevice, this.getFlowFromRootToNode(endDevice).size() - 1, i) -
                            this.getDepartureTime(endDevice, 0, i) + 
                            firstTransmissionDelay;
                }
                
                auxAverageLatency /= timeListSize;
                
                averageLatency += auxAverageLatency;
                
            }
            
            averageLatency /= this.pathTree.getLeaves().size();
            
        } else {
            // TODO: Throw error
            ;
        }
        
        return averageLatency;
    }
    
    public double getAverageLatencyToDevice(Device dev) {
        double averageLatency = 0;
        double auxAverageLatency = 0;
        Device endDevice = null;
     
        double firstTransmissionDelay = ((double)this.getPacketSize())/this.getFirstPortSpeed();
        
        ArrayList<FlowFragment> fragments = this.getFlowFromRootToNode(dev);
        
        for(int i = 0; i < this.getNumOfPacketsSent(); i++) {
            averageLatency += 
                    this.getScheduledTime(dev, fragments.size() - 1, i) -
                    this.getDepartureTime(dev, 0, i) + 
                    firstTransmissionDelay;
        }
        
        averageLatency = averageLatency / (this.getNumOfPacketsSent());
        
        
        return averageLatency;
    }

    /**
     * [Method]: getAverageJitter
     * [Usage]: Returns the average jitter of this flow.
     * Each absolute value resulting of the difference between
     * the last scheduled time, the first departure time and the 
     * average latency of the flow is added up to a variable.
     * The process is repeated to every packet sent by the starting
     * device. This sum is then divided by how many packets where 
     * sent.
     * 
     * @return      Average jitter of the flow
     */
    public double getAverageJitter() {
        double averageJitter = 0;
        double auxAverageJitter = 0;
        double averageLatency = this.getAverageLatency();   
        int timeListSize = 0;
        
        double firstTransmissionDelay = ((double)this.getPacketSize())/this.getFirstPortSpeed();
        
        if (type == UNICAST) {
            timeListSize = this.getTimeListSize();
            for(int i = 0; i < timeListSize; i++) {
                averageJitter += 
                    Math.abs(
                        this.getScheduledTime(this.flowFragments.size() - 1, i) -
                        this.getDepartureTime(0, i) +
                        firstTransmissionDelay -
                        averageLatency
                    );
            }
            
            averageJitter = averageJitter / (timeListSize);
        } else if(type == PUBLISH_SUBSCRIBE) {

            for(PathNode node : this.pathTree.getLeaves()) {
                
                auxAverageJitter = this.getAverageJitterToDevice(((Device) node.getNode()));
                averageJitter += auxAverageJitter;
                
            }
            
            averageJitter = averageJitter / this.pathTree.getLeaves().size();
        } else {
            // TODO: Throw error
            ;
        }
        
        return averageJitter;
    }
    
    
    /**
     * [Method]: getAverageJitterToDevice
     * [Usage]: From the path tree, retrieve the average jitter of 
     * the stream aimed at a specific device.
     * 
     * @param dev 		Specific end-device of the flow to retrieve the jitter
     * @return			Double value of the variation of the latency
     */
    public double getAverageJitterToDevice(Device dev) {
        double averageJitter = 0;
        double averageLatency = this.getAverageLatencyToDevice(dev);   

        ArrayList<FlowFragment> fragments = this.getFlowFromRootToNode(dev);
        
        double firstTransmissionDelay = ((double)this.getPacketSize())/this.getFirstPortSpeed();
        
        //this.printer.printIfLoggingIsEnabled( this.name + " average latency for " + dev.getName() + " of " + this.getAverageLatency());
        
        for(int i = 0; i < this.getNumOfPacketsSent(); i++) {
            averageJitter += 
                    Math.abs(
                        this.getScheduledTime(dev, this.getFlowFromRootToNode(dev).size() - 1, i) -
                        this.getDepartureTime(dev, 0, i) + 
                        firstTransmissionDelay - 
                        averageLatency
                    ); 
        }
        averageJitter = averageJitter/this.getNumOfPacketsSent();
        
        return averageJitter;
    }
    
    /**
     * [Method]: getLatency
     * [Usage]: Gets the Z3 variable containing the latency 
     * of the flow for a certain packet specified by the index.
     * 
     * @param solver    Solver in which the rules of the problem will be added
     * @param ctx       Z3 variable and function environment
     * @param index     Index of the desired packet
     * @return          Z3 variable containing the latency of the packet
     */
    public RealExpr getLatencyZ3(Solver solver, Context ctx, int index) {
        //index += 1;
        RealExpr latency = ctx.mkRealConst(this.name + "latencyOfPacket" + index);
        
        TSNSwitch lastSwitchInPath = ((TSNSwitch) this.path.get(path.size() - 1));
        FlowFragment lastFragmentInList = this.flowFragments.get(flowFragments.size() - 1);
        
        TSNSwitch firstSwitchInPath = ((TSNSwitch) this.path.get(0));
        FlowFragment firstFragmentInList = this.flowFragments.get(0);
        
        RealExpr firstTransmissionDelay = ctx.mkRealConst(Double.toString(this.getPacketSize()/this.getFirstPortSpeed()));
        
        solver.add(
            ctx.mkEq(latency, 
                ctx.mkAdd( firstTransmissionDelay ,
                		ctx.mkSub( lastSwitchInPath
                			.getPortOf(lastFragmentInList.getNextHop())
                        	.scheduledTime(ctx, index, lastFragmentInList),
                        	firstSwitchInPath.getPortOf(firstFragmentInList.getNextHop())
                        	.departureTime(ctx, index, firstFragmentInList)
                    )
                )
            )
        );
        
        
        return latency;
    }
    
    /**
     * [Method]: getLatencyZ3
     * [Usage]: Gets the Z3 variable containing the latency 
     * of the flow for a certain packet specified by the index
     * for a certain device.
     * 
     * @param solver    Solver in which the rules of the problem will be added
     * @param dev       End device of the packet
     * @param ctx       Z3 variable and function environment
     * @param index     Index of the desired packet
     * @return          Z3 variable containing the latency of the packet
     */
    public RealExpr getLatencyZ3(Solver solver, Device dev, Context ctx, int index) {
        //index += 1;

        RealExpr latency = ctx.mkRealConst(this.name + "latencyOfPacket" + index + "For" + dev.getName());
        
        ArrayList<PathNode> nodes = this.getNodesFromRootToNode(dev);
        ArrayList<FlowFragment> flowFrags = this.getFlowFromRootToNode(dev);
        
        TSNSwitch lastSwitchInPath = ((TSNSwitch) nodes.get(nodes.size() - 2).getNode()); // - 1 for indexing, - 1 for last node being the end device
        FlowFragment lastFragmentInList = flowFrags.get(flowFrags.size() - 1);
        
        TSNSwitch firstSwitchInPath = ((TSNSwitch) nodes.get(1).getNode()); // 1 since the first node is the publisher
        FlowFragment firstFragmentInList = flowFrags.get(0);

        RealExpr firstTransmissionDelay = ctx.mkRealConst(Double.toString(this.getPacketSize()/this.getFirstPortSpeed()));
        
        solver.add(
            ctx.mkEq(latency, 
                ctx.mkAdd( firstTransmissionDelay ,
                		ctx.mkSub( lastSwitchInPath
                			.getPortOf(lastFragmentInList.getNextHop())
                        	.scheduledTime(ctx, index, lastFragmentInList),
                        	firstSwitchInPath.getPortOf(firstFragmentInList.getNextHop())
                        	.departureTime(ctx, index, firstFragmentInList)
                    )
                )
            )
        );
        
        return latency;
    }
    
    /**
     * [Method]: getSumOfLatencyZ3
     * [Usage]: Recursively creates values to sum the z3 latencies
     * of the flow from 0 up to a certain packet.
     * 
     * @param solver    Solver in which the rules of the problem will be added
     * @param ctx       Z3 variable and function environment
     * @param index     Index of the current packet in the sum
     * @return          Z3 variable containing sum of latency up to index packet
     */
    public RealExpr getSumOfLatencyZ3(Solver solver, Context ctx, int index) {
        
        if(index == 0) {
            return getLatencyZ3(solver, ctx, 0);
        }
        
        return (RealExpr) ctx.mkAdd(getLatencyZ3(solver, ctx, index), getSumOfLatencyZ3(solver, ctx, index - 1));

    }
    
    /**
     * [Method]: getSumOfLatencyZ3
     * [Usage]: Recursively creates values to sum the z3 latencies
     * of the flow from 0 up to a certain packet for a certain device.
     * 
     * @param dev       Destination of the packet
     * @param solver    Solver in which the rules of the problem will be added
     * @param ctx       Z3 variable and function environment
     * @param index     Index of the current packet in the sum
     * @return          Z3 variable containing sum of latency up to index packet
     */
    public RealExpr getSumOfLatencyZ3(Device dev, Solver solver, Context ctx, int index) {
        if(index == 0) {
            return getLatencyZ3(solver, dev, ctx, 0);
        }
        
        return (RealExpr) ctx.mkAdd(getLatencyZ3(solver, dev, ctx, index), getSumOfLatencyZ3(dev, solver, ctx, index - 1));
    }
    
    /**
     * [Method]: getSumOfAllDevLatencyZ3
     * [Usage]: Returns the sum of all latency for all destinations
     * of the flow for the [index] number of packets sent.
     * 
     * @param solver    Solver in which the rules of the problem will be added
     * @param ctx       Z3 variable and function environment       
     * @param index     Number of packet sent (as index)
     * @return          Z3 variable containing the sum of all latencies of the flow
     */
    public RealExpr getSumOfAllDevLatencyZ3(Solver solver, Context ctx, int index) {
        RealExpr sumValue = ctx.mkReal(0);
        Device currentDev = null;
        
        for(PathNode node : this.pathTree.getLeaves()) {
            currentDev = (Device) node.getNode();
            sumValue = (RealExpr) ctx.mkAdd(this.getSumOfLatencyZ3(currentDev, solver, ctx, index), sumValue);   
        }
        
        return sumValue;
    }
    
    /**
     * [Method]: getSumOfAllDevLatencyZ3
     * [Usage]: Returns the sum of all latency for all destinations
     * of the flow for the [index] number of packets sent.
     * 
     * @param solver    Solver in which the rules of the problem will be added
     * @param ctx       Z3 variable and function environment       
     * @return          Z3 variable containing the average latency of the flow
     */
    public RealExpr getAvgLatency(Solver solver, Context ctx) {
        if(this.type == UNICAST) {
            return (RealExpr) ctx.mkDiv(
                getSumOfLatencyZ3(solver, ctx, this.numOfPacketsSentInFragment - 1), 
                ctx.mkReal(this.numOfPacketsSentInFragment)
            );
        } else if (this.type == PUBLISH_SUBSCRIBE) {
                return (RealExpr) ctx.mkDiv(
                    getSumOfAllDevLatencyZ3(solver, ctx, this.numOfPacketsSentInFragment - 1), 
                    ctx.mkReal((this.numOfPacketsSentInFragment) * this.pathTree.getLeaves().size())
                );
        } else {
            // TODO: THROW ERROR
        }
        
        return null;
    }
    
    
    /**
     * [Method]: getAvgLatency
     * [Usage]: Retrieves the average latency for one of the subscribers
     * of the flow.
     * 
     * @param dev 		Subscriber to which the average latency will be calculated
     * @param solver	Solver object 
     * @param ctx		Context object for the solver
     * @return			z3 variable with the average latency for the device
     */
    public RealExpr getAvgLatency(Device dev, Solver solver, Context ctx) {
        
        return (RealExpr) ctx.mkDiv(
            this.getSumOfLatencyZ3(dev, solver, ctx, this.numOfPacketsSentInFragment - 1), 
            ctx.mkReal(this.numOfPacketsSentInFragment)
        );
        
     }
    
    /**
     * [Method]: getJitterZ3
     * [Usage]: Returns the z3 variable containing the jitter of that
     * packet.
     * 
     * 
     * @param solver    Solver in which the rules of the problem will be added
     * @param ctx       Z3 variable and function environment       
     * @param index     Number of packet sent (as index)
     * @return          Z3 variable for the jitter of packet [index]
     */
    public RealExpr getJitterZ3(Solver solver, Context ctx, int index) {
        RealExpr avgLatency = this.getAvgLatency(solver, ctx);
        RealExpr latency = this.getLatencyZ3(solver, ctx, index);
        
        return (RealExpr) ctx.mkITE(
                ctx.mkGe(
                    latency, 
                    avgLatency
                ), 
                ctx.mkSub(latency , avgLatency),
                ctx.mkMul(
                    ctx.mkSub(latency , avgLatency), 
                    ctx.mkReal(-1)
                )
            );
        
    }
    
    /**
     * [Method]: getJitterZ3
     * [Usage]: Returns the z3 variable containing the jitter of that
     * packet.
     * 
     * 
     * @param solver    Solver in which the rules of the problem will be added
     * @param ctx       Z3 variable and function environment       
     * @param index     Number of packet sent (as index)
     * @return          Z3 variable for the jitter of packet [index]
     */
    public RealExpr getJitterZ3(Device dev, Solver solver, Context ctx, int index) {
        //index += 1;
        RealExpr jitter = ctx.mkRealConst(this.name + "JitterOfPacket" + index + "For" + dev.getName());
        
        ArrayList<PathNode> nodes = this.getNodesFromRootToNode(dev);
        
        TSNSwitch lastSwitchInPath = ((TSNSwitch) nodes.get(nodes.size() - 2).getNode()); // - 1 for indexing, - 1 for last node being the end device
        FlowFragment lastFragmentInList = nodes.get(nodes.size() - 2).getFlowFragments()
                        .get(nodes.get(nodes.size() - 2).getChildren().indexOf(nodes.get(nodes.size() - 1)));
        
        TSNSwitch firstSwitchInPath = ((TSNSwitch) nodes.get(1).getNode()); // 1 since the first node is the publisher
        FlowFragment firstFragmentInList = nodes.get(1).getFlowFragments().get(0); 
        
        double transmissionDelay = (((double) this.getPacketSize()) * 8 / 1000); //in microseconds
        
        // RealExpr avgLatency = (RealExpr) ctx.mkDiv(getSumOfLatencyZ3(solver, dev, ctx, index), ctx.mkInt(Network.PACKETUPPERBOUNDRANGE - 1));
        RealExpr avgLatency = this.getAvgLatency(dev, solver, ctx);
        RealExpr latency = (RealExpr) ctx.mkSub(
                                            lastSwitchInPath
                                            .getPortOf(lastFragmentInList.getNextHop())
                                            .scheduledTime(ctx, index, lastFragmentInList),
                                            ctx.mkSub(
                                            		firstSwitchInPath.getPortOf(firstFragmentInList.getNextHop())
                                            		.departureTime(ctx, index, firstFragmentInList)
                                            		, ctx.mkReal(Double.toString(transmissionDelay))
                                            )
                                      );
        
        solver.add(ctx.mkEq(jitter, 
                ctx.mkITE(
                    ctx.mkGe(latency, avgLatency),
                    ctx.mkSub(latency, avgLatency),
                    ctx.mkSub(avgLatency, latency)
                )
            
        ));
        
        return jitter;
    }
    
    /**
     * [Method]: getSumOfJitterZ3
     * [Usage]: Returns the sum of all jitter from packet 0
     * to packet of the given index as a Z3 variable.
     * 
     * @param solver    Solver in which the rules of the problem will be added
     * @param ctx       Z3 variable and function environment       
     * @param index     Number of packet sent (as index)
     * @return          Z3 variable containing the sum of all jitter
     */
    public RealExpr getSumOfJitterZ3(Solver solver, Context ctx, int index) {
        if(index == 0) {
            return getJitterZ3(solver, ctx, 0);
        }
        
        return (RealExpr) ctx.mkAdd(getJitterZ3(solver, ctx, index), getSumOfJitterZ3(solver, ctx, index - 1));
    }
    
    /**
     * [Method]: getSumOfJitterZ3
     * [Usage]: Returns the sum of all jitter from packet 0
     * to packet of the given index to a specific destination 
     * on a pub sub flow as a Z3 variable.
     * 
     * @param dev       Destination of the packet
     * @param solver    Solver in which the rules of the problem will be added
     * @param ctx       Z3 variable and function environment       
     * @param index     Number of packet sent (as index)
     * @return          Z3 variable containing the sum of all jitter
     */
    public RealExpr getSumOfJitterZ3(Device dev, Solver solver, Context ctx, int index) {
        if(index == 0) {
            return (RealExpr) getJitterZ3(dev, solver, ctx, 0);
        }
        
        return (RealExpr) ctx.mkAdd(getJitterZ3(dev, solver, ctx, index), getSumOfJitterZ3(dev, solver, ctx, index - 1));
    }
    
    /**
     * [Method]: getSumOfAllDevJitterZ3
     * [Usage]: Returns the sum of all jitter for all destinations
     * of the flow from 0 to the [index] packet.
     * 
     * @param solver    Solver in which the rules of the problem will be added
     * @param ctx       Z3 variable and function environment       
     * @param index     Number of packet sent (as index)
     * @return          Z3 variable containing the sum of all jitter of the flow
     */
    public RealExpr getSumOfAllDevJitterZ3(Solver solver, Context ctx, int index) {
        RealExpr sumValue = ctx.mkReal(0);
        Device currentDev = null;
        
        for(PathNode node : this.pathTree.getLeaves()) {
            currentDev = (Device) node.getNode();
            sumValue = (RealExpr) ctx.mkAdd(this.getSumOfJitterZ3(currentDev, solver, ctx, index), sumValue);   
        }
        
        return sumValue;
    }
    
    
    /**
     * [Method]: setNumberOfPacketsSent
     * [Usage]: Search through the flow fragments in order to find the highest
     * number of packets scheduled in a fragment. This is useful to set the hard
     * constraint for all packets scheduled within the flow.
     */
    
    public void setNumberOfPacketsSent(PathNode node) {
    	
    	if(node.getNode() instanceof Device && (node.getChildren().size() == 0)) {
			return;
		} else if (node.getNode() instanceof Device) {
			for(PathNode child : node.getChildren()) {
				this.setNumberOfPacketsSent(child);
			}
		} else {
			int index = 0;
			for(FlowFragment frag : node.getFlowFragments()) {
				if(this.numOfPacketsSentInFragment < frag.getNumOfPacketsSent()) {
					this.numOfPacketsSentInFragment = frag.getNumOfPacketsSent();
                }
				
				// this.printer.printIfLoggingIsEnabled("On node " + ((TSNSwitch)node.getNode()).getName() + " trying to reach children");			
				// this.printer.printIfLoggingIsEnabled("Node has: " + node.getFlowFragments().size() + " frags");
				// this.printer.printIfLoggingIsEnabled("Node has: " + node.getChildren().size() + " children");
				// for(PathNode n : node.getChildren()) {
				// 		this.printer.printIfLoggingIsEnabled("Child is a: " + (n.getNode() instanceof Device ? "Device" : "Switch"));
				// }
				
				this.setNumberOfPacketsSent(node.getChildren().get(index));
				index = index + 1;
			}
		}
		
    	
    }

 public void modifyIfUsingCustomVal(){
    	
    	if(this.flowSendingPeriodicity == -1) {
    		this.flowSendingPeriodicity = startDevice.getPacketPeriodicity();    		
    	}
    	if(this.flowFirstSendingTime == -1) {
    		this.flowFirstSendingTime = startDevice.getFirstT1Time();    		
    	}
    	if(this.packetSize == -1) { //Still parametrized to device
    		this.packetSize = (double) startDevice.getPacketSize();    		
    	}
    	if(this.flowMaximumLatency == -1) { //Still parametrized to device
    		this.flowMaximumLatency = startDevice.getHardConstraintTime();
    	}

    }

    /*
     * GETTERS AND SETTERS:
     */
    
    public int getHopPriority(String nameOfDestination) {
    	
    	PathNode pathNode = pathTree.searchNode(nameOfDestination, pathTree.getRoot());
		
		if(this.priorityValue!=-1) {
			return this.priorityValue;
		} else if(pathNode.getParent() == null) {
			return 1;
		} else {
			
			for(FlowFragment frag : pathNode.getParent().getFlowFragments()) {
				if(frag.getName().equals(nameOfDestination)) {
					return frag.getFragmentPriority();
				}
			}
			
		}
		
		return this.priorityValue;
    	
    }
    
    public Device getStartDevice() {
        return startDevice;
    }
    
    public void setStartDevice(Device startDevice) {
        this.startDevice = startDevice;


        if(this.type == this.PUBLISH_SUBSCRIBE) {
        	PathTree pathTree = new PathTree();
        	PathNode pathNode;
        	pathTree.addRoot(startDevice);
        	this.setPathTree(pathTree);        	
        }

    }

    public List<Device> getEndDeviceList() {
        return endDeviceList;
    }

	public void setEndDevice(Device endDevice) {
		
		if (this.type == UNICAST && this.endDeviceList.size()>0) {
			this.endDeviceList.clear();	
		} 
		
		this.endDeviceList.add(endDevice);

    }

    public ArrayList<Switch> getPath() {
        return path;
    }

    public void setPath(ArrayList<Switch> path) {
        this.path = path;
    }
    
    public IntExpr getFragmentPriorityZ3() {
        return flowPriority;
    }
    
    public void setFlowPriorityZ3(IntExpr priority) {
        this.flowPriority = priority;
    }
        
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArrayList<FlowFragment> getFlowFragments() {
        return flowFragments;
    }

    public void setFlowFragments(ArrayList<FlowFragment> flowFragments) {
        this.flowFragments = flowFragments;
    }
    
    public int getTimeListSize() {
        return this.getFlowFragments().get(0).getArrivalTimeList().size();
    }

    public PathTree getPathTree() {
        return pathTree;
    }

    public void setPathTree(PathTree pathTree) {
    	this.startDevice = (Device) pathTree.getRoot().getNode();
        this.pathTree = pathTree;
    }
    
    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
	
    public int getNumOfPacketsSent() {
		return numOfPacketsSentInFragment;
	}

	public void setNumOfPacketsSent(int numOfPacketsSent) {
		this.numOfPacketsSentInFragment = numOfPacketsSent;
	}

    public int getTotalNumOfPackets() {
		return totalNumOfPackets;
	}

	public void setTotalNumOfPackets(int totalNumOfPackets) {
		this.totalNumOfPackets = totalNumOfPackets;
	}
	
	public void addToTotalNumOfPackets(int num) {
		this.totalNumOfPackets = this.totalNumOfPackets + num;
	}
	
	public int getInstance() {
		return instance;
	}

	public void setInstance(int instance) {
		this.instance = instance;
	}
	
	public double getPacketSize() {
		return this.packetSize;
	}

	public double setPacketSize(double packetSize) {
		return this.packetSize = packetSize;
	}
	
	public RealExpr getPacketSizeZ3() {
		return this.startDevice.getPacketSizeZ3();
	}

    public double getFlowFirstSendingTime() {
        return flowFirstSendingTime;
    }

    public void setFlowFirstSendingTime(double flowFirstSendingTime) {
        this.flowFirstSendingTime = flowFirstSendingTime;
    }

    public double getFlowSendingPeriodicity() {
        return flowSendingPeriodicity;
    }

    public void setFlowSendingPeriodicity(double flowSendingPeriodicity) {
        this.flowSendingPeriodicity = flowSendingPeriodicity;
    }

    public RealExpr getFlowFirstSendingTimeZ3() {
        return flowFirstSendingTimeZ3;
    }

    public void setFlowFirstSendingTimeZ3(RealExpr flowFirstSendingTimeZ3) {
        this.flowFirstSendingTimeZ3 = flowFirstSendingTimeZ3;
    }

    public RealExpr getFlowSendingPeriodicityZ3() {
        return flowSendingPeriodicityZ3;
    }

    public void setFlowSendingPeriodicityZ3(RealExpr flowSendingPeriodicityZ3) {
        this.flowSendingPeriodicityZ3 = flowSendingPeriodicityZ3;
    }


	public boolean isFixedPriority() {
		return fixedPriority;
	}

	public void setFixedPriority(boolean fixedPriority) {
		this.fixedPriority = fixedPriority;
	}

	public int getPriorityValue() {
		return priorityValue;
	}

	public void setPriorityValue(int priorityValue) {
		this.priorityValue = priorityValue;
	}
	
    public static int getInstanceCounter() {
		return instanceCounter;
	}

	public static void setInstanceCounter(int instanceCounter) {
		Flow.instanceCounter = instanceCounter;
	}

	public Boolean getIsModifiedOrCreated() {
		return isModifiedOrCreated;
	}

	public void setIsModifiedOrCreated(Boolean isModifiedOrCreated) {
		this.isModifiedOrCreated = isModifiedOrCreated;
	}


    public double getFirstPortSpeed() {
    	return ((TSNSwitch) this.pathTree
                .getRoot()
                .getChildren()
                .get(0)
                .getNode())
                .getPortOf(this.getStartDevice().getName())
                .getPortSpeed();
    }


	public double getFlowMaximumJitter() {
		return flowMaximumJitter;
	}


	public void setFlowMaximumJitter(double flowJitter) {
		this.flowMaximumJitter = flowJitter;
	}


    public double getFlowMaximumLatency() {
		return flowMaximumLatency;
	}

	public void setFlowMaximumLatency(double flowMaximumLatency) {
		this.flowMaximumLatency = flowMaximumLatency;
	}
	
	public String getStartDeviceName() {
		return this.startDevice.getName();
	}


	public Printer getPrinter() {
		return printer;
	}


	public void setPrinter(Printer printer) {
		this.printer = printer;
	}

}
