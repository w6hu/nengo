/*
The contents of this file are subject to the Mozilla Public License Version 1.1 
(the "License"); you may not use this file except in compliance with the License. 
You may obtain a copy of the License at http://www.mozilla.org/MPL/

Software distributed under the License is distributed on an "AS IS" basis, WITHOUT
WARRANTY OF ANY KIND, either express or implied. See the License for the specific 
language governing rights and limitations under the License.

The Original Code is "DecodedOrigin.java". Description: 
"An Origin of functions of the state variables of an NEFEnsemble"

The Initial Developer of the Original Code is Bryan Tripp & Centre for Theoretical Neuroscience, University of Waterloo. Copyright (C) 2006-2008. All Rights Reserved.

Alternatively, the contents of this file may be used under the terms of the GNU 
Public License license (the GPL License), in which case the provisions of GPL 
License are applicable  instead of those above. If you wish to allow use of your 
version of this file only under the terms of the GPL License and not to allow 
others to use your version of this file under the MPL, indicate your decision 
by deleting the provisions above and replace  them with the notice and other 
provisions required by the GPL License.  If you do not delete the provisions above,
a recipient may use your version of this file under either the MPL or the GPL License.
*/

/*
 * Created on 2-Jun-2006
 */
package ca.nengo.model.nef.impl;

import org.apache.log4j.Logger;

import ca.nengo.math.Function;
import ca.nengo.math.LinearApproximator;
import ca.nengo.model.InstantaneousOutput;
import ca.nengo.model.Node;
import ca.nengo.model.Noise;
import ca.nengo.model.Origin;
import ca.nengo.model.RealOutput;
import ca.nengo.model.Resettable;
import ca.nengo.model.SimulationException;
import ca.nengo.model.SimulationMode;
import ca.nengo.model.SpikeOutput;
import ca.nengo.model.StructuralException;
import ca.nengo.model.Units;
import ca.nengo.model.impl.RealOutputImpl;
import ca.nengo.model.nef.NEFEnsemble;
import ca.nengo.util.MU;
import ca.nengo.util.VectorGenerator;
import ca.nengo.util.impl.RandomHypersphereVG;

/**
 * An Origin of functions of the state variables of an NEFEnsemble. 
 * 
 * TODO: how do units fit in. define in constructor? ignore?
 * TODO: select nodes make up decoded origin 
 * 
 * @author Bryan Tripp
 */
public class DecodedOrigin implements Origin, Resettable, SimulationMode.ModeConfigurable, Noise.Noisy {

	private static final long serialVersionUID = 1L;
	
	private static Logger ourLogger = Logger.getLogger(DecodedOrigin.class);
	
	private Node myNode; //parent node
	private String myName;
	private Node[] myNodes;
	private String myNodeOrigin;
	private Function[] myFunctions;
	private float[][] myDecoders;
	private SimulationMode myMode;
	private RealOutput myOutput;
	private Noise myNoise = null;
	private Noise[] myNoises = null;

	/**
	 * With this constructor, decoding vectors are generated using default settings. 
	 *  
	 * @param node The parent Node
	 * @param name Name of this Origin
	 * @param nodes Nodes that belong to the NEFEnsemble from which this Origin arises
	 * @param nodeOrigin Name of the Origin on each given node from which output is to be decoded  
	 * @param functions Output Functions on the vector that is represented by the NEFEnsemble 
	 * 		(one Function per dimension of output). For example if the Origin is to output 
	 * 		x1*x2, where the ensemble represents [x1 x1], then one 2D function would be 
	 * 		needed in this list. The input dimension of each function must be the same as the 
	 * 		dimension of the state vector represented by this ensemble.  
	 * @throws StructuralException if functions do not all have the same input dimension (we 
	 * 		don't check against the state dimension at this point)
	 */
	public DecodedOrigin(Node node, String name, Node[] nodes, String nodeOrigin, Function[] functions, LinearApproximator approximator) 
			throws StructuralException {
		
		checkFunctionDimensions(functions);
		
		myNode = node;
		myName = name;
		myNodes = nodes;
		myNodeOrigin = nodeOrigin;
		myFunctions = functions; 
		myDecoders = findDecoders(nodes, functions, approximator);  
		myMode = SimulationMode.DEFAULT;
		
		reset(false);
	}
	
	/**
	 * With this constructor decoding vectors are specified by the caller. 
	 * 
	 * @param node The parent Node
	 * @param name As in other constructor
	 * @param nodes As in other constructor
	 * @param nodeOrigin Name of the Origin on each given node from which output is to be decoded  
	 * @param functions As in other constructor
	 * @param decoders Decoding vectors which are scaled by the main output of each Node, and 
	 * 		then summed, to estimate the same function of the ensembles state vector that is 
	 * 		defined by the 'functions' arg. The 'functions' arg is still needed, because in DIRECT 
	 * 		SimulationMode, these functions are used directly. The 'decoders' arg allows the caller 
	 * 		to provide decoders that are generated with non-default methods or parameters (eg an 
	 * 		unusual number of singular values). Must be a matrix with one row per Node and one 
	 * 		column per function.   
	 * @throws StructuralException If dimensions.length != neurons.length, decoders is not a matrix 
	 * 		(ie all elements with same length), or if the number of columns in decoders is not equal 
	 * 		to the number of functions 
	 */
	public DecodedOrigin(Node node, String name, Node[] nodes, String nodeOrigin, Function[] functions, float[][] decoders) throws StructuralException {
		checkFunctionDimensions(functions);
		
		if (!MU.isMatrix(decoders)) {
			throw new StructuralException("Elements of decoders do not all have the same length");
		}
		
		if (decoders[0].length != functions.length) {
			throw new StructuralException("Number of decoding functions and dimension of decoding vectors must be the same");
		}
		
		if (decoders.length != nodes.length) {
			throw new StructuralException("Number of decoding vectors and Neurons must be the same");
		}
			
		myNode = node;
		myName = name;
		myNodes = nodes;
		myNodeOrigin = nodeOrigin;
		myFunctions = functions;
		myDecoders = decoders;
		myMode = SimulationMode.DEFAULT;
		
		reset(false);
	}
	
	/**
	 * @return Mean-squared error of this origin over randomly selected points  
	 */
	public float[] getError() {
		float[] result = new float[getDimensions()];
		
		if (myNode instanceof NEFEnsemble) {
			NEFEnsemble ensemble = (NEFEnsemble) myNode;
			
			VectorGenerator vg = new RandomHypersphereVG(false, 1, 0);			
			float[][] unscaled = vg.genVectors(500, ensemble.getDimension());
			float[][] input = new float[unscaled.length][];
			for (int i = 0; i < input.length; i++) {
				input[i] = MU.prodElementwise(unscaled[i], ensemble.getRadii());
			}
			
			float[][] idealOutput = NEFUtil.getOutput(this, input, SimulationMode.DIRECT);
			float[][] actualOutput = NEFUtil.getOutput(this, input, SimulationMode.CONSTANT_RATE);
			
			float[][] error = MU.transpose(MU.difference(actualOutput, idealOutput));
			for (int i = 0; i < error.length; i++) {
				result[i] = MU.prod(error[i], error[i]) / (float) error[i].length;
			}			
		} else {
			ourLogger.warn("Can't calculate error of a DecodedOrigin unless it belongs to an NEFEnsemble");
		}
		
		return result;
	}
	
	/**
	 * @param noise New output noise model (defaults to no noise)
	 */
	public void setNoise(Noise noise) {
		myNoise = noise;
		myNoises = new Noise[getDimensions()];
		for (int i = 0; i < myNoises.length; i++) {
			myNoises[i] = myNoise.clone();
		}
	}
	
	/**
	 * @return Noise with which output of this Origin is corrupted
	 */
	public Noise getNoise() {
		return myNoise;
	}

	/**
	 * @see ca.nengo.model.Resettable#reset(boolean)
	 */
	public void reset(boolean randomize) {
		float time = (myOutput == null) ? 0 : myOutput.getTime();
		myOutput = new RealOutputImpl(new float[myFunctions.length], Units.UNK, time);
		
		if (myNoise != null) myNoise.reset(randomize);
		if (myNoises != null) {
			for (int i = 0; i < myNoises.length; i++) {
				myNoises[i].reset(randomize);
			}
		}
	}

	private static float[][] findDecoders(Node[] nodes, Function[] functions, LinearApproximator approximator)  {
		float[][] result = new float[nodes.length][];
		for (int i = 0; i < result.length; i++) {
			result[i] = new float[functions.length];
		}
		
		for (int j = 0; j < functions.length; j++) {
			float[] coeffs = approximator.findCoefficients(functions[j]);
			for (int i = 0; i < nodes.length; i++) {
				result[i][j] = coeffs[i];
			}
		}
		
		return result;
	}
		
	private static void checkFunctionDimensions(Function[] functions) throws StructuralException {
		int dim = functions[0].getDimension();
		for (int i = 1; i < functions.length; i++) {
			if (functions[i].getDimension() != dim) {
				throw new StructuralException("Functions must all have the same input dimension");
			}
		}
	}
	
	/**
	 * @see ca.nengo.model.Origin#getName()
	 */
	public String getName() {
		return myName;
	}

	/**
	 * @see ca.nengo.model.Origin#getDimensions()
	 */
	public int getDimensions() {
		return myFunctions.length;
	}
	
	/**
	 * @return Decoding vectors for each Node
	 */
	public float[][] getDecoders() {
		return myDecoders;
	}

	/**
	 * @param decoders New decoding vectors (row per Node)
	 */
	public void setDecoders(float[][] decoders) {
		assert MU.isMatrix(decoders);
		assert myDecoders.length == decoders.length;
		assert myDecoders[0].length == decoders[0].length;
		
		myDecoders = decoders;
	}
	
	/**
	 * @param mode Requested simulation mode 
	 */
	public void setMode(SimulationMode mode) {
		myMode = mode;
	}
	
	/**
	 * @return The mode in which the Ensemble is currently running. 
	 */
	public SimulationMode getMode() {
		return myMode;
	}
	
	/**
	 * Must be called at each time step after Nodes are run and before getValues().  
	 *  
	 * @param state Idealized state (as defined by inputs) which can be fed into (idealized) functions 
	 * 		that make up the Origin, when it is running in DIRECT mode. This is not used in other modes, 
	 * 		and can be null.
	 * @param startTime simulation time of timestep onset  
	 * @param endTime simulation time of timestep end  
	 * @throws SimulationException If the given state is not of the expected dimension (ie the input 
	 * 		dimension of the functions provided in the constructor)
	 */
	public void run(float[] state, float startTime, float endTime) throws SimulationException {
		if (state != null && state.length != myFunctions[0].getDimension()) {
			throw new SimulationException("A state of dimension " + myFunctions[0].getDimension() + " was expected");
		}		
		
		float[] values = new float[myFunctions.length];
		float stepSize = endTime - startTime;
		
		if (myMode == SimulationMode.DIRECT) {
			for (int i = 0; i < values.length; i++) {
				values[i] = myFunctions[i].map(state);
			}
		} else {
			for (int i = 0; i < myNodes.length; i++) {
				try {
					InstantaneousOutput o = myNodes[i].getOrigin(myNodeOrigin).getValues();

					float val = 0; 
					if (o instanceof SpikeOutput) {
						val = ((SpikeOutput) o).getValues()[0] ? 1f / stepSize : 0f;
					} else if (o instanceof RealOutput) {
						val = ((RealOutput) o).getValues()[0];
					} else {
						throw new Error("Node output is of type " + o.getClass().getName() 
							+ ". DecodedOrigin can only deal with RealOutput and SpikeOutput, so it apparently has to be updated");
					}
					
					float[] decoder = myDecoders[i];
					for (int j = 0; j < values.length; j++) {
						values[j] += val * decoder[j];
					}
				} catch (StructuralException e) {
					throw new SimulationException(e);
				}				
			}		
		}
		
		if (myNoise != null) {
			for (int i = 0; i < values.length; i++) {
				values[i] = myNoises[i].getValue(startTime, endTime, values[i]);				
			}
		}
		
		myOutput = new RealOutputImpl(values, Units.UNK, endTime);
	}
	
	/**
	 * @see ca.nengo.model.Origin#getValues()
	 */
	public InstantaneousOutput getValues() throws SimulationException {
		return myOutput;
	}
	
	/**
	 * @return List of Functions approximated by this DecodedOrigin
	 */
	public Function[] getFunctions() {
		return myFunctions;
	}

	/**
	 * @return Name of Node-level Origin on which this DecodedOrigin is based
	 */
	protected String getNodeOrigin() {
		return myNodeOrigin;
	}

	/**
	 * @see ca.nengo.model.Origin#getNode()
	 */
	public Node getNode() {
		return myNode;
	}

	@Override
	public Origin clone() throws CloneNotSupportedException {
		Function[] functions = new Function[myFunctions.length];
		for (int i = 0; i < functions.length; i++) {
			functions[i] = myFunctions[i].clone();
		}
		try {
			DecodedOrigin result = new DecodedOrigin(myNode, myName, myNodes, myNodeOrigin, functions, MU.clone(myDecoders));
			result.myOutput = (RealOutput) myOutput.clone();
			result.setNoise(myNoise.clone());
			result.setMode(myMode);
			return result;
		} catch (StructuralException e) {
			throw new CloneNotSupportedException("Error trying to clone: " + e.getMessage());
		}
	}

}