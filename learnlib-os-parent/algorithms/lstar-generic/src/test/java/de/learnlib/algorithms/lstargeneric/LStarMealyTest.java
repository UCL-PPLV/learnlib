/* Copyright (C) 2013 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 * 
 * LearnLib is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 3.0 as published by the Free Software Foundation.
 * 
 * LearnLib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with LearnLib; if not, see
 * <http://www.gnu.de/documents/lgpl.en.html>.
 */
package de.learnlib.algorithms.lstargeneric;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.automatalib.automata.transout.MealyMachine;
import net.automatalib.automata.transout.impl.FastMealy;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Symbol;

import org.testng.annotations.Test;

import de.learnlib.algorithms.lstargeneric.ce.ClassicLStarCEXHandler;
import de.learnlib.algorithms.lstargeneric.ce.ObservationTableCEXHandler;
import de.learnlib.algorithms.lstargeneric.ce.ShahbazCEXHandler;
import de.learnlib.algorithms.lstargeneric.ce.Suffix1by1CEXHandler;
import de.learnlib.algorithms.lstargeneric.closing.CloseFirstStrategy;
import de.learnlib.algorithms.lstargeneric.closing.CloseLexMinStrategy;
import de.learnlib.algorithms.lstargeneric.closing.CloseRandomStrategy;
import de.learnlib.algorithms.lstargeneric.closing.CloseShortestStrategy;
import de.learnlib.algorithms.lstargeneric.closing.ClosingStrategy;
import de.learnlib.algorithms.lstargeneric.mealy.ClassicLStarMealy;
import de.learnlib.algorithms.lstargeneric.mealy.ExtensibleLStarMealy;
import de.learnlib.api.EquivalenceOracle;
import de.learnlib.api.LearningAlgorithm;
import de.learnlib.api.MembershipOracle;
import de.learnlib.eqtests.basic.SimulatorEQOracle;
import de.learnlib.eqtests.basic.mealy.SymbolEQOracleWrapper;
import de.learnlib.examples.mealy.ExampleStack;
import de.learnlib.oracles.SimulatorOracle;

public class LStarMealyTest extends LearningTest {

	@Test
	public void testClassicLStarMealy() {
		FastMealy<Symbol,String> mealy = ExampleStack.constructMachine();
		Alphabet<Symbol> alphabet = mealy.getInputAlphabet();
		
		MembershipOracle<Symbol,Word<String>> oracle
			= new SimulatorOracle<>(mealy);
			
		List<ObservationTableCEXHandler<Symbol,String>> cexHandlers
			= Arrays.asList(ClassicLStarCEXHandler.<Symbol,String>getInstance(),
			ShahbazCEXHandler.<Symbol,String>getInstance(),
			Suffix1by1CEXHandler.<Symbol,String>getInstance());
		
		List<ClosingStrategy<Symbol,String>> closingStrategies
			= Arrays.asList(CloseFirstStrategy.<Symbol,String>getInstance(),
					CloseLexMinStrategy.<Symbol,String>getInstance(),
					CloseRandomStrategy.<Symbol,String>getInstance(),
					CloseShortestStrategy.<Symbol,String>getInstance());
			
		

		// Empty list of suffixes => minimal compliant set
		List<Word<Symbol>> initSuffixes = Collections.emptyList();
		
		EquivalenceOracle<? super MealyMachine<?,Symbol,?,String>, Symbol, Word<String>> mealyEqOracle
					= new SimulatorEQOracle<>(mealy);
					
		EquivalenceOracle<? super MealyMachine<?,Symbol,?,String>, Symbol, String> mealySymEqOracle
			= new SymbolEQOracleWrapper<>(mealyEqOracle);
		
		for(ObservationTableCEXHandler<Symbol,String> handler : cexHandlers) {
			for(ClosingStrategy<Symbol,String> strategy : closingStrategies) {
				LearningAlgorithm<MealyMachine<?,Symbol,?,String>,Symbol,String> learner
				= ClassicLStarMealy.createForWordOracle(alphabet, oracle, initSuffixes,
						handler, strategy);
				
				testLearnModel(mealy, alphabet, learner, mealySymEqOracle);
			}
		}
	}
	
	@Test
	public void testOptimizedLStarMealy() {
		FastMealy<Symbol,String> mealy = ExampleStack.constructMachine();
		Alphabet<Symbol> alphabet = mealy.getInputAlphabet();
		
		MembershipOracle<Symbol,Word<String>> oracle
			= new SimulatorOracle<>(mealy);
			
		List<ObservationTableCEXHandler<Symbol,Word<String>>> cexHandlers
			= Arrays.asList(ClassicLStarCEXHandler.<Symbol,Word<String>>getInstance(),
			ShahbazCEXHandler.<Symbol,Word<String>>getInstance(),
			Suffix1by1CEXHandler.<Symbol,Word<String>>getInstance());
		
		List<ClosingStrategy<Symbol,Word<String>>> closingStrategies
			= Arrays.asList(CloseFirstStrategy.<Symbol,Word<String>>getInstance(),
					CloseLexMinStrategy.<Symbol,Word<String>>getInstance(),
					CloseRandomStrategy.<Symbol,Word<String>>getInstance(),
					CloseShortestStrategy.<Symbol,Word<String>>getInstance());
			
		
		// Empty list of suffixes => minimal compliant set
		List<Word<Symbol>> initSuffixes = Collections.emptyList();
		
		EquivalenceOracle<? super MealyMachine<?,Symbol,?,String>, Symbol, Word<String>> mealyEqOracle
				= new SimulatorEQOracle<>(mealy);
		
		for(ObservationTableCEXHandler<Symbol,Word<String>> handler : cexHandlers) {
			for(ClosingStrategy<Symbol,Word<String>> strategy : closingStrategies) {
				LearningAlgorithm<MealyMachine<?,Symbol,?,String>,Symbol,Word<String>> learner
				= new ExtensibleLStarMealy<>(alphabet, oracle, initSuffixes,
						handler, strategy);
				
				testLearnModel(mealy, alphabet, learner, mealyEqOracle);
			}
		}
	}

}
