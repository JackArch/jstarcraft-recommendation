package com.jstarcraft.recommendation.evaluator.rating;

import java.util.Iterator;
import java.util.List;

import com.jstarcraft.ai.utility.Int2FloatKeyValue;
import com.jstarcraft.recommendation.evaluator.RatingEvaluator;

import it.unimi.dsi.fastutil.floats.FloatCollection;

/**
 * 平均相对误差评估器
 * 
 * <pre>
 * MPE = Mean Prediction  Error
 * </pre>
 *
 * @author Birdy
 */
public class MPEEvaluator extends RatingEvaluator {

	private float mpe;

	public MPEEvaluator(float mpe) {
		this.mpe = mpe;
	}

	@Override
	protected float measure(FloatCollection checkCollection, List<Int2FloatKeyValue> recommendList) {
		float value = 0F;
		Iterator<Float> iterator = checkCollection.iterator();
		for (Int2FloatKeyValue keyValue : recommendList) {
			float score = iterator.next();
			float estimate = keyValue.getValue();
			if (Math.abs(score - estimate) > mpe) {
				value++;
			}
		}
		return value;
	}

}
