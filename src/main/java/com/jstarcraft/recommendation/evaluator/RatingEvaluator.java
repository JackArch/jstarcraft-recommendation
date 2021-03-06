package com.jstarcraft.recommendation.evaluator;

import java.util.List;

import com.jstarcraft.ai.utility.Int2FloatKeyValue;

import it.unimi.dsi.fastutil.floats.FloatCollection;

/**
 * 面向评分预测的评估器
 * 
 * @author Birdy
 *
 */
public abstract class RatingEvaluator extends AbstractEvaluator<FloatCollection> {

	@Override
	protected int count(FloatCollection checkCollection, List<Int2FloatKeyValue> recommendList) {
		return checkCollection.size();
	}

}
