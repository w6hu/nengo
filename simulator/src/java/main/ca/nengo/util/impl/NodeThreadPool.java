package ca.nengo.util.impl;

//import ca.nengo.model.InstantaneousOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import ca.nengo.model.Network;
import ca.nengo.model.Node;
import ca.nengo.model.Projection;
import ca.nengo.util.TaskSpawner;
import ca.nengo.util.ThreadTask;

/**
 * A pool of threads for running nodes in. All interaction with the threads
 * is done through this class.
 *
 * @author Eric Crawford
 */
public class NodeThreadPool {
	protected static final int maxNumJavaThreads = 100;
	protected static final int defaultNumJavaThreads = 8;


	// numThreads can change throughout a simulation run. Therefore, it should not be used during a run,
	// only at the beginning of a run to create the threads.
	protected static int myNumJavaThreads = defaultNumJavaThreads;
	protected int myNumThreads;
	protected NodeThread[] myThreads;
	protected Object myLock;

	protected Node[] myNodes;
	protected Projection[] myProjections;
    protected ThreadTask[] myTasks;

	protected volatile int numThreadsComplete;

	protected volatile boolean threadsRunning;
	protected volatile boolean runFinished;
	protected float myStartTime;
	protected float myEndTime;
	
	protected static boolean myCollectTimings;
	protected long myRunStartTime;
	protected double myAverageTimePerStep;
	protected int myNumSteps;

	public static int getNumJavaThreads(){
		return myNumJavaThreads;
	}

	public static void setNumJavaThreads(int value){
		myNumJavaThreads = value;
	}
	
	public static int getMaxNumJavaThreads(){
		return maxNumJavaThreads;
	}
	

	public static boolean isMultithreading(){
		return myNumJavaThreads != 0;
	}

	// to turn it back on, call setNumThreads with a positive value
	public static void turnOffMultithreading(){
		myNumJavaThreads = 0;
	}

	public static boolean isCollectingTimings() {
		return myCollectTimings;
	}

	public static void setCollectTimings(boolean collectTimings) {
		myCollectTimings = collectTimings;
	}
	
	public float getStartTime(){
		return myStartTime;
	}

	public float getEndTime(){
		return myEndTime;
	}

	public boolean getRunFinished(){
		return runFinished;
	}

	// Dummy default constructor.
	protected NodeThreadPool(){
	}
	
	public NodeThreadPool(Network network, List<ThreadTask> threadTasks){
		initialize(network, threadTasks);
	}
	
	/**
	 * 1. Checks whether the GPU is to be used for the simulation. If it is, creates
	 * a GPU Thread, passes this thread the nodes and projections which are to be run on the GPU,
	 * and calls the initialization function of the gpu thread's NEFGPUInterface. Starts the GPU thread.
	 * 
	 * 2. Creates the appropriate number of java threads and assigns to each a fair number of
	 * projections, nodes and tasks from those that remain after the GPU data has been dealt with.
	 * Starts the Java threads.
	 * 
	 * 3. Initializes synchronization primitives and variables for collecting timing data if applicable.
	 * 
	 * @author Eric Crawford
	 */
	protected void initialize(Network network, List<ThreadTask> threadTasks){
		
		myLock = new Object();
		
		Node[] nodes = network.getNodes();
		Projection[] projections = network.getProjections();
		
		List<Node> nodeList = collectNodes(nodes, false);
		List<Projection> projList = collectProjections(nodes, projections);
		List<ThreadTask> taskList = collectTasks(nodes);
		taskList.addAll(threadTasks);
		
		myNodes = nodeList.toArray(new Node[0]);
		myProjections = projList.toArray(new Projection[0]);
		myTasks = taskList.toArray(new ThreadTask[0]);
		
		threadsRunning = false;
		runFinished = false;
		
		boolean useGPU = NEFGPUInterface.getUseGPU();
		
		if(useGPU){
			myNumThreads = myNumJavaThreads + 1;
	    }else{
	    	myNumThreads = myNumJavaThreads;
	    }
		
		myThreads = new NodeThread[myNumThreads];
		
		if(useGPU){ 
			GPUThread gpuThread = new GPUThread(this);
			
			// The NEFGPUInterface removes from myNodes ensembles that are to be run on the GPU and returns the rest.
			myNodes = gpuThread.getNEFGPUInterface().takeGPUNodes(myNodes);
			
			// The NEFGPUInterface removes from myProjections projections that are to be run on the GPU and returns the rest.
			myProjections = gpuThread.getNEFGPUInterface().takeGPUProjections(myProjections);
			
			gpuThread.getNEFGPUInterface().initialize();
			
			
			gpuThread.setCollectTimings(myCollectTimings);
			gpuThread.setName("GPUThread0");
			
			myThreads[myNumJavaThreads] = gpuThread;
			
			gpuThread.setPriority(Thread.MAX_PRIORITY);
			gpuThread.start();
		}
		
		//In the remaining nodes (non-GPU nodes), DO break down the NetworkArrays, we don't want to call the 
		// "run" method of nodes which are members of classes which derive from the NetworkImpl class since 
		// NetworkImpls create their own LocalSimulators when run.
		myNodes = collectNodes(myNodes, true).toArray(new Node[0]);

		int nodesPerJavaThread = (int) Math.ceil((float) myNodes.length / (float) myNumJavaThreads);
		int projectionsPerJavaThread = (int) Math.ceil((float) myProjections.length / (float) myNumJavaThreads);
        int tasksPerJavaThread = (int) Math.ceil((float) myTasks.length / (float) myNumJavaThreads);

		int nodeOffset = 0, projectionOffset = 0, taskOffset = 0;
		int nodeStartIndex, nodeEndIndex, projectionStartIndex, projectionEndIndex, taskStartIndex, taskEndIndex;

		
		// Evenly distribute projections, nodes and tasks to the java threads.
		for(int i = 0; i < myNumJavaThreads; i++){

			nodeStartIndex = nodeOffset;
			nodeEndIndex = myNodes.length - nodeOffset >= nodesPerJavaThread ?
					nodeOffset + nodesPerJavaThread : myNodes.length;

			nodeOffset += nodesPerJavaThread;

			projectionStartIndex = projectionOffset;
			projectionEndIndex = myProjections.length - projectionOffset >= projectionsPerJavaThread ?
					projectionOffset + projectionsPerJavaThread : myProjections.length;

			projectionOffset += projectionsPerJavaThread;

			taskStartIndex = taskOffset;
			taskEndIndex = myTasks.length - taskOffset >= tasksPerJavaThread ?
					taskOffset + tasksPerJavaThread : myTasks.length;

			taskOffset += tasksPerJavaThread;

			myThreads[i] = new NodeThread(this, myNodes, nodeStartIndex,
					nodeEndIndex, myProjections, projectionStartIndex,
					projectionEndIndex, myTasks, taskStartIndex, taskEndIndex);
			
			myThreads[i].setCollectTimings(myCollectTimings);
			myThreads[i].setName("JavaThread" + i);

			myThreads[i].setPriority(Thread.MAX_PRIORITY);
			myThreads[i].start();
		}
		
		myRunStartTime = myCollectTimings ? new Date().getTime() : 0;
		myAverageTimePerStep = 0;
		myNumSteps = 0;
	}

	/**
	 * Tell the threads in the current thread pool to take a step. The step consists of three
	 * phases: projections, nodes, tasks. All threads must complete a stage before any thread begins
	 * the next stage, so, for example, all threads must finish processing all of their projections 
	 * before any thread starts processing its nodes.
	 * 
	 * @author Eric Crawford
	 */
	public void step(float startTime, float endTime){
		myStartTime = startTime;
		myEndTime = endTime;
		
		
		long stepInterval = myCollectTimings ? new Date().getTime() : 0;
		
		try
		{
			int oldPriority = Thread.currentThread().getPriority();
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

			// start the projection processing, wait for it to finish
			startThreads();

			// start the node processing, wait for it to finish
			startThreads();

			// start the task processing, wait for it to finish
			startThreads();

			Thread.currentThread().setPriority(oldPriority);
		}
		catch(Exception e)
		{}
		
		if(myCollectTimings){
			stepInterval = new Date().getTime() - stepInterval;
			myAverageTimePerStep = (myAverageTimePerStep * myNumSteps + stepInterval) / (myNumSteps + 1);
            
            myNumSteps++;
		}
	}

	/**
	 * Tells the threads to run for one phase (projections, nodes or tasks). 
	 * The threads should be waiting on myLock at the time this is called.
	 * 
	 * @author Eric Crawford
	 */
	private void startThreads() throws InterruptedException {
		synchronized(myLock){
			numThreadsComplete = 0;
			threadsRunning = true;

			myLock.notifyAll();
			myLock.wait();
		}
	}

	/**
	 * Called by the threads in this node pool. Called once they finish a phase (projections, nodes or tasks).
	 * Forces them to wait on myLock.
	 * 
	 * @author Eric Crawford
	 */
	public void threadWait() throws InterruptedException{
		synchronized(myLock){
			while(!threadsRunning) {
                myLock.wait();
            }
		}
	}

	/**
	 * Called by the threads in this pool to signal that they are done a phase. 
	 * 
	 * @author Eric Crawford
	 */
	public void threadFinished() throws InterruptedException{
		synchronized(myLock){
			numThreadsComplete++;

			if(numThreadsComplete == myThreads.length){
				threadsRunning = false;
				myLock.notifyAll();
			}

			myLock.wait();

			threadWait();
		}
	}

	/**
	 * Kill the threads in the pool by interrupting them. Each thread will handle
	 * the interrupt signal by ending its run method, which kills it.
	 * 
	 * @author Eric Crawford
	 */
	public void kill(){
		synchronized(myLock)
		{
			threadsRunning = true;
			runFinished = true;

			for(int i = 0; i < myThreads.length; i++){
				myThreads[i].interrupt();
			}
			
			if(myCollectTimings){
				StringBuffer timingOutput = new StringBuffer();
				timingOutput.append("Timings for NodeThreadPool:\n");
				
				long approxRunTime = new Date().getTime() - myRunStartTime;
				timingOutput.append("Approximate total run time: " + approxRunTime + " ms\n");
				
				timingOutput.append("Average time per step: " + myAverageTimePerStep + " ms\n");
				
				System.out.print(timingOutput.toString());
			}

			myLock.notifyAll();
		}
		
		
	}
	
    /**
     * Return all the nodes in the network except subnetworks. Essentially returns a "flattened"
     * version of the network. The breakDownNetworkArrays param lets the caller choose whether to include
     * Network Arrays in the returned list (=false) or to return the NEFEnsemble in the network array (=true).
     * This facility is provided because sometimes Network Arrays should be treated like Networks, which is what
     * they are as far as java is concerned (they extend the NetworkImpl class), and sometimes it is better to 
     * treat them like NEFEnsembles, which they are designed to emulate (they're supposed to be an 
     * easier-to-build version of large NEFEnsembles).
     * 
     * @author Eric Crawford
     */
    public static List<Node> collectNodes(Node[] startingNodes, boolean breakDownNetworkArrays){

        ArrayList<Node> nodes = new ArrayList<Node>();

        List<Node> nodesToProcess = new LinkedList<Node>();
        nodesToProcess.addAll(Arrays.asList(startingNodes));

        Node workingNode;

        boolean isNetwork = false;
        while(nodesToProcess.size() != 0)
        {
            workingNode = nodesToProcess.remove(0);
            
            //Decide whether to break the node into its subnodes
            if((workingNode.getClass().getCanonicalName().contains("CCMModelNetwork"))){
            	isNetwork = false;
            }
            else if(workingNode.getClass().getCanonicalName() == "org.python.proxies.nef.array$NetworkArray$6")
            {
            	if(breakDownNetworkArrays){
            		isNetwork = true;
            	}else{
            		isNetwork = false;
            	}
            }
            else if(workingNode instanceof Network){
            	isNetwork = true;
            }
            else{
                isNetwork = false;
            }
            
            
            if(isNetwork){
            	List<Node> nodeList = new LinkedList<Node>(Arrays.asList(((Network) workingNode).getNodes()));

                nodeList.addAll(nodesToProcess);
                nodesToProcess = nodeList;
            }
            else{
            	nodes.add(workingNode);
            } 
        }

        return nodes;
    }
    

    /**
     * Return all the projections in the network. Essentially returns all the projections that
     * would be in a "flattened" version of the network.
     * 
     * @author Eric Crawford
     */
    public static List<Projection> collectProjections(Node[] startingNodes, Projection[] startingProjections){

        ArrayList<Projection> projections = new ArrayList<Projection>(Arrays.asList(startingProjections));

        List<Node> nodesToProcess = new LinkedList<Node>();
        nodesToProcess.addAll(Arrays.asList(startingNodes));

        Node workingNode;

        while(nodesToProcess.size() != 0)
        {
            workingNode = nodesToProcess.remove(0);

            if(workingNode instanceof Network) {
                List<Node> nodeList = new LinkedList<Node>(Arrays.asList(((Network) workingNode).getNodes()));

                nodeList.addAll(nodesToProcess);
                nodesToProcess = nodeList;

                projections.addAll(Arrays.asList(((Network) workingNode).getProjections()));
            }
        }

        return projections;
    }

    /**
     * Return all the tasks in the network. Essentially returns all the tasks that
     * would be in a "flattened" version of the network.
     * 
     * @author Eric Crawford
     */
    public static List<ThreadTask> collectTasks(Node[] startingNodes){

        ArrayList<ThreadTask> tasks = new ArrayList<ThreadTask>();

        List<Node> nodesToProcess = new LinkedList<Node>();
        nodesToProcess.addAll(Arrays.asList(startingNodes));

        Node workingNode;

        while(nodesToProcess.size() != 0)
        {
            workingNode = nodesToProcess.remove(0);

            if(workingNode instanceof Network && !(workingNode.getClass().getCanonicalName().contains("CCMModelNetwork")))
            {
                List<Node> nodeList = new LinkedList<Node>(Arrays.asList(((Network) workingNode).getNodes()));

                nodeList.addAll(nodesToProcess);
                nodesToProcess = nodeList;
            }
            else if(workingNode instanceof TaskSpawner)
            {
                tasks.addAll(Arrays.asList(((TaskSpawner) workingNode).getTasks()));
            }
        }

        return tasks;
    }
}
