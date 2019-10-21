package com.indago.metaseg;

import com.indago.fg.Assignment;
import com.indago.fg.Factor;
import com.indago.fg.LinearConstraint;
import com.indago.fg.Relation;
import com.indago.fg.UnaryCostConstraintGraph;
import com.indago.fg.Variable;
import gnu.trove.impl.Constants;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class SolveOjalgo {

	public static File defaultLogFileDirectory = new File( "." );
	private final File logFileDirectory;
	ExpressionsBasedModel model = new ExpressionsBasedModel();

	public SolveOjalgo() {
		this( defaultLogFileDirectory );
	}

	public SolveOjalgo( final File logFileDirectory ) {
		this.logFileDirectory = logFileDirectory;
	}

	/**
	 * Solves a given factor graph.
	 *
	 * @param fg
	 *            the factor graph to be solved.
	 * @return 
	 * 
	 * @return an <code>Assignment</code> containing the solution.
	 */
	public OjalgoResult solve( final UnaryCostConstraintGraph fg ) {

		if ( model != null ) {
			// Dispose of model
			model.dispose();
		}
		
//		ExpressionsBasedModel.addFallbackSolver( SolverGurobi.INTEGRATION );
//		ExpressionsBasedModel.addPreferredSolver( SolverGurobi.INTEGRATION );
		model = new ExpressionsBasedModel();
		BasicLogger.debug( SolveOjalgo.class.getSimpleName() );
		BasicLogger.debug();

		final Collection< Variable > variables = fg.getVariables();
		final Collection< Factor > unaries = fg.getUnaries();
		final Collection< Factor > constraints = fg.getConstraints();

		final int NO_VALUE = -1;
		final TObjectIntMap< Variable > variableToIndex = new TObjectIntHashMap<>( variables.size(), Constants.DEFAULT_LOAD_FACTOR, NO_VALUE );
		int variableIndex = 0;
		for ( final Variable v : variables )
			variableToIndex.put( v, variableIndex++ );

		// Set objective: minimize costs
		double constantTerm = 0;
		final double[] objectiveCoeffs = new double[ variables.size() ];
		for ( final Factor factor : unaries ) {
			final Variable variable = factor.getVariables().get( 0 );
			final double cost0 = factor.getFunction().evaluate( 0 );
			final double cost1 = factor.getFunction().evaluate( 1 );
			constantTerm += cost0;
			double coeff = 0;
			if ( variableToIndex.get( variable ) != NO_VALUE ) {
				coeff = ( cost1 - cost0 ) + objectiveCoeffs[ variableToIndex.get( variable ) ]; //variableToCoeff.get( variable );
			}
			objectiveCoeffs[ variableToIndex.get( variable ) ] = coeff;
		}
		for(int i = 0; i< objectiveCoeffs.length; i++) {
			double weight = objectiveCoeffs[i];
			String varName = "Var" + Integer.toString( i );
			model.addVariable( org.ojalgo.optimisation.Variable.make( varName ).lower( 0 ).upper( 1 ).weight( weight ).integer( true ) );
		}
		model.addVariable( org.ojalgo.optimisation.Variable.make( "constantTerm" ).lower( constantTerm ).upper( constantTerm ).weight( 1 ) );

		// Add constraints.
		Expression exp[] = new Expression[ constraints.size() ];
		int expCount = 0;
		for ( final Factor factor : constraints ) {
			final int arity = factor.getArity();
			final LinearConstraint constr = ( LinearConstraint ) factor.getFunction();
			final double[] constrCoeffs = constr.getCoefficients();
			final List< Variable > fv = factor.getVariables();
			final String[] constrVars = new String[ arity ];
			double comp = constr.getRhs();
			Relation relation = constr.getRelation();
			exp[ expCount ] = model.addExpression();
			if ( relation.name() == "LE" ) {
				exp[ expCount ].upper( comp );
			} else if ( relation.name() == "GE" ) {
				exp[ expCount ].lower( comp );
			} else if ( relation.name() == "EQ" ) {
				exp[ expCount ].lower( comp ).upper( comp );
			}

			for ( int i = 0; i < arity; ++i ) {
				int idx = variableToIndex.get( fv.get( i ) );
				constrVars[ i ] = "Var" + Integer.toString( idx );
				exp[ expCount ].set( idx, constrCoeffs[ i ] );

			}
			expCount = expCount + 1;

		}
		Optimisation.Result result = model.minimise();
		BasicLogger.debug();
		BasicLogger.debug( result );
		BasicLogger.debug();
		BasicLogger.debug( model );
		BasicLogger.debug();
		final int[] vals = new int[ result.size() ];
		for ( int i = 0; i < result.size(); i++ ) {
			vals[ i ] = Math.round( result.get( i ).floatValue() );
		}
		return new OjalgoResult( variableToIndex, vals, model );

	}

	public static class OjalgoResult implements Assignment< Variable > {

		private final TObjectIntMap< Variable > variableToIndex;

		private final int[] vals;

		private final ExpressionsBasedModel OjalgoModel;

		public OjalgoResult(
				final TObjectIntMap< Variable > variableToIndex,
				final int[] vals,
				final ExpressionsBasedModel model ) {
			this.variableToIndex = variableToIndex;
			this.vals = vals;
			this.OjalgoModel = model;
		}

		@Override
		public boolean isAssigned( final Variable var ) {
			return variableToIndex.containsKey( var );
		}

		@Override
		public int getAssignment( final Variable var ) {
			return vals[ variableToIndex.get( var ) ];
		}

		/**
		 * @return the variableToIndex
		 */
		public TObjectIntMap< Variable > getVariableToIndex() {
			return variableToIndex;
		}
	}

}
