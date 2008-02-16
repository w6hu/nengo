/*
 * Created on 6-Jun-2006
 */
package ca.neo.dynamics.impl;

import ca.neo.model.Units;
import ca.neo.util.MU;

/**
 * <p>A linear time-invariant system with the following properties:
 *   
 * <ul>
 * <li>A diagonal dynamics matrix</li>
 * <li>A zero passthrough matrix</li>
 * </ul>
 * 
 * This implementation will run faster than an instance of the superclass that 
 * has these properties.</p> 
 * 
 * @author Bryan Tripp
 */
public class SimpleLTISystem extends LTISystem {

	private static final long serialVersionUID = 1L;
	
	private float[] A;
	private float[][] B; 
	private float[][] C;

	/**
	 * See also LTISystem. 
	 * 
	 * @param A Diagonal entries of dynamics matrix
	 * @param B Input matrix
	 * @param C Output matrix
	 * @param x0 Initial state
	 * @param outputUnits Units in which each dimension of the output are expressed 
	 */
	public SimpleLTISystem(float[] A, float[][] B, float[][] C, float[] x0, Units[] outputUnits) {
		super(MU.diag(A), B, C, MU.zero(C.length, B[0].length), x0, outputUnits);
		
		this.A = A;
		this.B = B;
		this.C = C;
	}
	
	/**
	 * Creates an appropriately-dimensioned system with all-zero matrices, so that elements 
	 * can be changed later. 
	 *  
	 * @param stateDim Number of state variables
	 * @param inputDim Number of inputs
	 * @param outputDim Number of outputs
	 */
	public SimpleLTISystem(int stateDim, int inputDim, int outputDim) {
		this(new float[stateDim], MU.zero(stateDim, inputDim), MU.zero(outputDim, stateDim), 
				new float[stateDim], Units.uniform(Units.UNK, outputDim));
	}
	
	/**
	 * @see ca.neo.dynamics.DynamicalSystem#f(float, float[])
	 */
	public float[] f(float t, float[] u) {
		assert u.length == getInputDimension();
		
		float[] x = getState();
		float[] result = new float[x.length];
		
		for (int i = 0; i < result.length; i++) {
			result[i] = A[i] * x[i];
			
			for (int j = 0; j < u.length; j++) {
				result[i] += B[i][j] * u[j]; 
			}
		}
		
		return result;
	}

	/**
	 * @see ca.neo.dynamics.DynamicalSystem#g(float, float[])
	 */
	public float[] g(float t, float[] u) {
		assert u.length == getInputDimension();
		
		return MU.prod(C, getState());
	}

	@Override
	public void setA(float[][] newA) {
		float[] newAVector = MU.diag(newA);
		super.setA(MU.diag(newAVector));
		this.A = newAVector;
	}

	@Override
	public void setB(float[][] newB) {
		super.setB(newB);
		this.B = newB;
	}

	@Override
	public void setC(float[][] newC) {
		super.setC(newC);
		this.C = newC;
	}
	
}
