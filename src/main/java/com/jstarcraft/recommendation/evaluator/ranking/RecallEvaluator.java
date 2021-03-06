package com.jstarcraft.recommendation.evaluator.ranking;

import java.util.List;

import com.jstarcraft.ai.utility.Int2FloatKeyValue;
import com.jstarcraft.recommendation.evaluator.RankingEvaluator;

import it.unimi.dsi.fastutil.ints.IntCollection;

/**
 * 召回率评估器
 * 
 * <pre>
 * https://en.wikipedia.org/wiki/Precision_and_recall
 * </pre>
 *
 * @author Birdy
 */
public class RecallEvaluator extends RankingEvaluator {

	public RecallEvaluator(int size) {
		super(size);
	}

	@Override
	protected float measure(IntCollection checkCollection, List<Int2FloatKeyValue> recommendList) {
		if (recommendList.size() > size) {
			recommendList = recommendList.subList(0, size);
		}
		int count = 0;
		for (Int2FloatKeyValue keyValue : recommendList) {
			if (checkCollection.contains(keyValue.getKey())) {
				count++;
			}
		}
		return count / (checkCollection.size() + 0F);
	}

}
