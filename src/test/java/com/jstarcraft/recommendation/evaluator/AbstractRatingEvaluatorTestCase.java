package com.jstarcraft.recommendation.evaluator;

import java.util.ArrayList;
import java.util.List;

import com.jstarcraft.ai.utility.Int2FloatKeyValue;
import com.jstarcraft.recommendation.recommender.Recommender;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatCollection;
import it.unimi.dsi.fastutil.floats.FloatList;

public abstract class AbstractRatingEvaluatorTestCase extends AbstractEvaluatorTestCase<FloatCollection> {

	@Override
	protected FloatCollection check(int userIndex) {
		int from = testPaginations[userIndex], to = testPaginations[userIndex + 1];
		FloatList scoreList = new FloatArrayList(to - from);
		for (int index = from, size = to; index < size; index++) {
			int position = testPositions[index];
			scoreList.add(testMarker.getMark(position));
		}
		return scoreList;
	}

	@Override
	protected List<Int2FloatKeyValue> recommend(Recommender recommender, int userIndex) {
		int from = testPaginations[userIndex], to = testPaginations[userIndex + 1];
		int[] discreteFeatures = new int[testMarker.getQualityOrder()];
		float[] continuousFeatures = new float[testMarker.getQuantityOrder()];
		List<Int2FloatKeyValue> recommendList = new ArrayList<>(to - from);
		for (int index = from, size = to; index < size; index++) {
			int position = testPositions[index];
			for (int dimension = 0; dimension < testMarker.getQualityOrder(); dimension++) {
				discreteFeatures[dimension] = testMarker.getQualityFeature(dimension, position);
			}
			for (int dimension = 0; dimension < testMarker.getQuantityOrder(); dimension++) {
				continuousFeatures[dimension] = testMarker.getQuantityFeature(dimension, position);
			}
			recommendList.add(new Int2FloatKeyValue(discreteFeatures[itemDimension], recommender.predict(discreteFeatures, continuousFeatures)));
		}
		return recommendList;
	}

}
