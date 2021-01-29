package hanMiner.operator.featureExtraction.vectorizer;

import com.hankcs.hanlp.mining.word.TfIdfCounter;
import com.rapidminer.example.Attribute;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.*;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.tools.Ontology;
import hanMiner.text.SimpleDocumentSet;
import hanMiner.text.DocumentSet;

import java.util.*;

/**
 *
 * This operator can vectorize a set of documents using TF-IDF. Stopwords will be
 * removed. The output is an n*m example set {@link ExampleSet} (n: number of documents,
 * m: number of features). The result can be fed into next-step NLP models.
 *
 * @author joeyhaohao
 */

public class TfIdfVectorizer extends Operator {

    private static final String PARAMETER_MAX_FEATURES = "max_features";

    private InputPort documentSetInput = getInputPorts().createPort("text");
    private OutputPort exampleSetOutput = getOutputPorts().createPort("example set");

    public TfIdfVectorizer(OperatorDescription description) {
        super(description);
    }

    @Override
    public List<ParameterType> getParameterTypes() {
        List<ParameterType> types = super.getParameterTypes();
        ParameterType type = new ParameterTypeInt(
                PARAMETER_MAX_FEATURES,
                "This parameter specifies the max number of features in the result. " +
                        "The vocabulary will be built by top max_features ordered by term frequency " +
                        "across the corpus.",
                1,
                500,
                100,
                false);
        types.add(type);

        return types;
    }

    @Override
    public void doWork() throws OperatorException {
        DocumentSet documentSet = documentSetInput.getData(SimpleDocumentSet.class);
        int maxFeatureNum = getParameterAsInt(PARAMETER_MAX_FEATURES);

        TfIdfCounter tfIdf = new TfIdfCounter(true);
        for (int i = 0; i < documentSet.size(); i++) {
            tfIdf.add(i, documentSet.getDocument(i));
        }

        // Clip max number of features ordered by term frequency
        int featureNum = Math.min(tfIdf.allTf().size(), maxFeatureNum);
        List<Map.Entry<String, Double>> allTf = tfIdf.sortedAllTf().subList(0, featureNum);
        HashMap<String, Integer> word2featureMap = new HashMap<>();
        int ind = 0;
        for (Map.Entry<String, Double> tf: allTf){
            word2featureMap.put(tf.getKey(), ind++);
        }

        // Create new example set of vectors
        List<Attribute> listOfAtts = new LinkedList<>();
        for (int i = 0; i < featureNum; i++) {
            Attribute newNumericalAtt = AttributeFactory.createAttribute(
                    "Feature_" + i,
                    Ontology.ATTRIBUTE_VALUE_TYPE.REAL);
            listOfAtts.add(newNumericalAtt);
        }
        MemoryExampleTable table = new MemoryExampleTable(listOfAtts);

        for (Map.Entry<Object, Map<String, Double>> entry: tfIdf.compute().entrySet()) {
            Map<String, Double> tfIdfMap = entry.getValue();
            double[] doubleArray = new double[listOfAtts.size()];
            Arrays.fill(doubleArray, 0.0);

            for (String word: tfIdfMap.keySet()){
                if (word2featureMap.containsKey(word)) {
                    int index = word2featureMap.get(word);
                    doubleArray[index] = tfIdfMap.get(word);
                }
            }
            table.addDataRow(new DoubleArrayDataRow(doubleArray));
        }

        ExampleSet exampleSet = table.createExampleSet();
        exampleSetOutput.deliver(exampleSet);
    }
}
