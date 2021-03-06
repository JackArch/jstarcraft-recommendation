package com.jstarcraft.recommendation.recommender.neuralnetwork;

import java.util.HashMap;
import java.util.Map;

import org.nd4j.linalg.factory.Nd4j;

import com.jstarcraft.ai.math.structure.DenseCache;
import com.jstarcraft.ai.math.structure.MathCache;
import com.jstarcraft.ai.math.structure.MathCalculator;
import com.jstarcraft.ai.math.structure.matrix.DenseMatrix;
import com.jstarcraft.ai.math.structure.vector.SparseVector;
import com.jstarcraft.ai.model.neuralnetwork.Graph;
import com.jstarcraft.ai.model.neuralnetwork.GraphConfigurator;
import com.jstarcraft.ai.model.neuralnetwork.activation.IdentityActivationFunction;
import com.jstarcraft.ai.model.neuralnetwork.activation.ReLUActivationFunction;
import com.jstarcraft.ai.model.neuralnetwork.activation.SigmoidActivationFunction;
import com.jstarcraft.ai.model.neuralnetwork.layer.EmbedLayer;
import com.jstarcraft.ai.model.neuralnetwork.layer.Layer;
import com.jstarcraft.ai.model.neuralnetwork.layer.ParameterConfigurator;
import com.jstarcraft.ai.model.neuralnetwork.layer.WeightLayer;
import com.jstarcraft.ai.model.neuralnetwork.learn.SgdLearner;
import com.jstarcraft.ai.model.neuralnetwork.loss.BinaryXENTLossFunction;
import com.jstarcraft.ai.model.neuralnetwork.normalization.IgnoreNormalizer;
import com.jstarcraft.ai.model.neuralnetwork.optimization.StochasticGradientOptimizer;
import com.jstarcraft.ai.model.neuralnetwork.parameter.NormalParameterFactory;
import com.jstarcraft.ai.model.neuralnetwork.schedule.ConstantSchedule;
import com.jstarcraft.ai.model.neuralnetwork.schedule.Schedule;
import com.jstarcraft.ai.model.neuralnetwork.vertex.LayerVertex;
import com.jstarcraft.ai.model.neuralnetwork.vertex.ShareVertex;
import com.jstarcraft.ai.model.neuralnetwork.vertex.accumulation.OuterProductVertex;
import com.jstarcraft.ai.model.neuralnetwork.vertex.operation.PlusVertex;
import com.jstarcraft.ai.model.neuralnetwork.vertex.operation.ShiftVertex;
import com.jstarcraft.ai.model.neuralnetwork.vertex.transformation.HorizontalAttachVertex;
import com.jstarcraft.core.utility.RandomUtility;
import com.jstarcraft.recommendation.configure.Configuration;
import com.jstarcraft.recommendation.data.DataSpace;
import com.jstarcraft.recommendation.data.accessor.DenseModule;
import com.jstarcraft.recommendation.data.accessor.SampleAccessor;
import com.jstarcraft.recommendation.recommender.ModelRecommender;

/**
 * Created by zhhy on 2018/8/6.
 */
public class DeepCrossRecommender extends ModelRecommender {
    /**
     * the learning rate of the optimization algorithm
     */
    protected float learnRate;

    /**
     * the momentum of the optimization algorithm
     */
    protected float momentum;

    /**
     * the regularization coefficient of the weights in the neural network
     */
    protected float weightRegularization;

    /**
     * 所有维度的特征总数
     */
    private int numberOfFeatures;

    /**
     * the data structure that stores the training data N个样本 f个filed
     */
    protected DenseMatrix[] inputData;

    /**
     * the data structure that stores the predicted data
     */
    protected DenseMatrix outputData;

    /**
     * 计算图
     */
    protected Graph graph;

    protected SampleAccessor marker;

    protected int[] dimensionSizes;

    @Override
    public void prepare(Configuration configuration, SampleAccessor marker, DenseModule model, DataSpace space) {
        super.prepare(configuration, marker, model, space);
        learnRate = configuration.getFloat("rec.iterator.learnrate");
        momentum = configuration.getFloat("rec.iterator.momentum");
        weightRegularization = configuration.getFloat("rec.weight.regularization");
        this.marker = marker;

        dimensionSizes = new int[marker.getQualityOrder()];
        int orderIndex = 0;
        for (String name : marker.getQualityFields()) {
            dimensionSizes[orderIndex++] = space.getQualityAttribute(name).getSize();
        }
    }

    protected Graph getComputationGraph(int[] dimensionSizes) {
        Schedule schedule = new ConstantSchedule(learnRate);
        GraphConfigurator configurator = new GraphConfigurator();
        Map<String, ParameterConfigurator> configurators = new HashMap<>();
        Nd4j.getRandom().setSeed(6L);
        ParameterConfigurator parameter = new ParameterConfigurator(weightRegularization, 0F, new NormalParameterFactory());
        configurators.put(WeightLayer.WEIGHT_KEY, parameter);
        configurators.put(WeightLayer.BIAS_KEY, new ParameterConfigurator(0F, 0F));
        MathCache factory = new DenseCache();

        // 构建Embed节点

        int numberOfFactors = 10;
        String[] embedVertexNames = new String[dimensionSizes.length];
        for (int fieldIndex = 0; fieldIndex < dimensionSizes.length; fieldIndex++) {
            embedVertexNames[fieldIndex] = "Embed" + fieldIndex;
            Layer embedLayer = new EmbedLayer(dimensionSizes[fieldIndex], numberOfFactors, factory, configurators, new IdentityActivationFunction());
            configurator.connect(new LayerVertex(embedVertexNames[fieldIndex], factory, embedLayer, new SgdLearner(schedule), new IgnoreNormalizer()));
        }

        // 构建Net Input节点
        int numberOfHiddens = 20;
        configurator.connect(new HorizontalAttachVertex("EmbedStack", factory), embedVertexNames);
        configurator.connect(new ShiftVertex("EmbedStack0", factory, 0F), "EmbedStack");
        Layer netLayer = new WeightLayer(dimensionSizes.length * numberOfFactors, numberOfHiddens, factory, configurators, new ReLUActivationFunction());
        configurator.connect(new LayerVertex("NetInput", factory, netLayer, new SgdLearner(schedule), new IgnoreNormalizer()), "EmbedStack");

        // cross net
        // 构建crossNet

        int numberOfCrossLayers = 3;

        for (int crossLayerIndex = 0; crossLayerIndex < numberOfCrossLayers; crossLayerIndex++) {
            if (crossLayerIndex == 0) {
                configurator.connect(new OuterProductVertex("OuterProduct" + crossLayerIndex, factory), "EmbedStack0", "EmbedStack"); // （n,fk*fk)
            } else {
                configurator.connect(new OuterProductVertex("OuterProduct" + crossLayerIndex, factory), "EmbedStack" + crossLayerIndex, "EmbedStack"); // （n,fk*fk)
            }

            // // 水平切割
            // String[] outerProductShare=new String[dimensionSizes.length *
            // numberOfFactors];
            // for(int shareIndex=0;shareIndex<dimensionSizes.length *
            // numberOfFactors;shareIndex++)
            // {
            // int from=shareIndex*dimensionSizes.length * numberOfFactors;
            // int end=(shareIndex+1)*dimensionSizes.length * numberOfFactors;
            // configurator.connect(new
            // HorizontalUnstackVertex("OuterProductShare"+shareIndex+crossLayerIndex,factory,from,end),
            // "OuterProduct"+crossLayerIndex);
            // outerProductShare[shareIndex]="OuterProductShare"+shareIndex+crossLayerIndex;
            // }
            //
            // // 水平堆叠
            // configurator.connect(new
            // HorizontalStackVertex("OuterProductShareStack"+crossLayerIndex,factory),outerProductShare);

            Layer crossLayer = new WeightLayer(dimensionSizes.length * numberOfFactors, 1, factory, configurators, new IdentityActivationFunction());
            configurator.connect(new ShareVertex("OutProduct_cross" + crossLayerIndex, factory, dimensionSizes.length * numberOfFactors, crossLayer), "OuterProduct" + crossLayerIndex); // (n,fk)

            if (crossLayerIndex == 0) {
                configurator.connect(new PlusVertex("EmbedStack" + (crossLayerIndex + 1), factory), "OutProduct_cross" + crossLayerIndex, "EmbedStack"); // (n,fk)
            } else {
                configurator.connect(new PlusVertex("EmbedStack" + (crossLayerIndex + 1), factory), "OutProduct_cross" + crossLayerIndex, "EmbedStack" + crossLayerIndex); // (n,fk)
            }
        }

        // dnn
        int numberOfLayers = 5;
        String currentLayer = "NetInput";
        for (int layerIndex = 0; layerIndex < numberOfLayers; layerIndex++) {
            Layer hiddenLayer = new WeightLayer(numberOfHiddens, numberOfHiddens, factory, configurators, new SigmoidActivationFunction());
            configurator.connect(new LayerVertex("NetHidden" + layerIndex, factory, hiddenLayer, new SgdLearner(schedule), new IgnoreNormalizer()), currentLayer);
            currentLayer = "NetHidden" + layerIndex;
        }

        // 构建Deep Output节点
        configurator.connect(new HorizontalAttachVertex("DeepStack", factory), currentLayer, "EmbedStack" + numberOfCrossLayers);
        Layer deepLayer = new WeightLayer(dimensionSizes.length * numberOfFactors + numberOfHiddens, 1, factory, configurators, new SigmoidActivationFunction());
        configurator.connect(new LayerVertex("DeepOutput", factory, deepLayer, new SgdLearner(schedule), new IgnoreNormalizer()), "DeepStack");

        Graph graph = new Graph(configurator, new StochasticGradientOptimizer(), new BinaryXENTLossFunction(false));
        return graph;
    }

    @Override
    protected void doPractice() {

        int[] positiveKeys = new int[dimensionSizes.length], negativeKeys = new int[dimensionSizes.length];

        graph = getComputationGraph(dimensionSizes);

        for (int iterationStep = 1; iterationStep <= numberOfEpoches; iterationStep++) {
            totalLoss = 0F;

            // TODO 应该调整为配置项.
            int batchSize = 2000;
            inputData = new DenseMatrix[dimensionSizes.length];
            // inputData[dimensionSizes.length] = DenseMatrix.valueOf(batchSize,
            // dimensionSizes.length);
            for (int index = 0; index < dimensionSizes.length; index++) {
                inputData[index] = DenseMatrix.valueOf(batchSize, 1);
            }
            DenseMatrix labelData = DenseMatrix.valueOf(batchSize, 1);

            for (int batchIndex = 0; batchIndex < batchSize;) {
                // 随机用户
                int userIndex = RandomUtility.randomInteger(numberOfUsers);
                SparseVector userVector = trainMatrix.getRowVector(userIndex);
                if (userVector.getElementSize() == 0 || userVector.getElementSize() == numberOfItems) {
                    continue;
                }

                int from = dataPaginations[userIndex], to = dataPaginations[userIndex + 1];
                // 获取正样本
                int positivePosition = dataPositions[RandomUtility.randomInteger(from, to)];
                for (int index = 0; index < positiveKeys.length; index++) {
                    positiveKeys[index] = marker.getQualityFeature(index, positivePosition);
                }

                // 获取负样本
                int negativeItemIndex = RandomUtility.randomInteger(numberOfItems - userVector.getElementSize());
                for (int position = 0, size = userVector.getElementSize(); position < size; position++) {
                    if (negativeItemIndex >= userVector.getIndex(position)) {
                        negativeItemIndex++;
                        continue;
                    }
                    break;
                }
                // TODO 注意,此处为了故意制造负面特征.
                int negativePosition = dataPositions[RandomUtility.randomInteger(from, to)];
                for (int index = 0; index < negativeKeys.length; index++) {
                    negativeKeys[index] = marker.getQualityFeature(index, negativePosition);
                }
                negativeKeys[itemDimension] = negativeItemIndex;

                for (int dimension = 0; dimension < dimensionSizes.length; dimension++) {
                    // inputData[dimension].putScalar(batchIndex, 0,
                    // positiveKeys[dimension]);
                    // inputData[dimensionSizes.length].setValue(batchIndex, dimension,
                    // positiveKeys[dimension]);
                    inputData[dimension].setValue(batchIndex, 0, positiveKeys[dimension]);
                }
                labelData.setValue(batchIndex, 0, 1);
                batchIndex++;

                for (int dimension = 0; dimension < dimensionSizes.length; dimension++) {
                    // inputData[dimension].putScalar(batchIndex, 0,
                    // negativeKeys[dimension]);
                    // inputData[dimensionSizes.length].setValue(batchIndex, dimension,
                    // negativeKeys[dimension]);
                    inputData[dimension].setValue(batchIndex, 0, negativeKeys[dimension]);
                }
                labelData.setValue(batchIndex, 0, 0);
                batchIndex++;
            }
            totalLoss = graph.practice(100, inputData, new DenseMatrix[] { labelData });

            DenseMatrix[] data = new DenseMatrix[inputData.length];
            DenseMatrix label = DenseMatrix.valueOf(10, 1);
            for (int index = 0; index < data.length; index++) {
                DenseMatrix input = inputData[index];
                data[index] = DenseMatrix.valueOf(10, input.getColumnSize());
                data[index].iterateElement(MathCalculator.SERIAL, (scalar) -> {
                    scalar.setValue(input.getValue(scalar.getRow(), scalar.getColumn()));
                });
            }
            graph.predict(data, new DenseMatrix[] { label });
            System.out.println(label);

            if (isConverged(iterationStep) && isConverged) {
                break;
            }
            currentLoss = totalLoss;
        }

        // inputData[dimensionSizes.length] = DenseMatrix.valueOf(numberOfUsers,
        // dimensionSizes.length);
        for (int index = 0; index < dimensionSizes.length; index++) {
            inputData[index] = DenseMatrix.valueOf(numberOfUsers, 1);
        }

        for (int dimension = 0; dimension < dimensionSizes.length; dimension++) {
            if (dimension != itemDimension) {
                for (int userIndex = 0; userIndex < numberOfUsers; userIndex++) {
                    int position = dataPositions[dataPaginations[userIndex + 1] - 1];
                    int feature = marker.getQualityFeature(dimension, position);
                    // inputData[dimension].putScalar(userIndex, 0,
                    // keys[dimension]);
                    // inputData[dimensionSizes.length].setValue(userIndex, dimension, feature);
                    inputData[dimension].setValue(userIndex, 0, feature);
                }
            }
        }

        DenseMatrix labelData = DenseMatrix.valueOf(numberOfUsers, 1);
        outputData = DenseMatrix.valueOf(numberOfUsers, numberOfItems);

        for (int itemIndex = 0; itemIndex < numberOfItems; itemIndex++) {
            // inputData[dimensionSizes.length].getColumnVector(itemDimension).calculate(VectorMapper.constantOf(itemIndex),
            // null, Calculator.SERIAL);
            inputData[itemDimension].setValues(itemIndex);
            graph.predict(inputData, new DenseMatrix[] { labelData });
            outputData.getColumnVector(itemIndex).iterateElement(MathCalculator.SERIAL, (scalar) -> {
                scalar.setValue(labelData.getValue(scalar.getIndex(), 0));
            });
        }
    }

    @Override
    public float predict(int[] dicreteFeatures, float[] continuousFeatures) {
        int userIndex = dicreteFeatures[userDimension];
        int itemIndex = dicreteFeatures[itemDimension];
        float value = outputData.getValue(userIndex, itemIndex);
        return value;
    }

}
