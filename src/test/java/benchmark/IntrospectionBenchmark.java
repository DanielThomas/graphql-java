package benchmark;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(3)
public class IntrospectionBenchmark {

    private final GraphQL graphQL;
    private final DFCountingInstrumentation countingInstrumentation = new DFCountingInstrumentation();

    static class DFCountingInstrumentation extends SimplePerformantInstrumentation {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, Long> times = new LinkedHashMap<>();

        @Override
        public @NotNull DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
            return (DataFetcher<Object>) env -> {
                long then = System.nanoTime();
                Object value = dataFetcher.get(env);
                long nanos = System.nanoTime() - then;
                DataFetcherResult.Builder<Object> result = DataFetcherResult.newResult().data(value);

                String path = env.getExecutionStepInfo().getPath().toString();
                String prevTypePath = env.getLocalContext();

                Object source = env.getSource();
                if (isSchemaTypesFetch(env, source)) {
                    String typeName = ((GraphQLNamedType) source).getName();

                    String prefix = "/__schema/types[" + typeName + "]";
                    result.localContext(prefix);
                    prevTypePath = prefix;
                }
                if (prevTypePath != null) {
                    path = path.replaceAll("/__schema/types\\[.*\\]", prevTypePath);
                }
                counts.compute(path, (k, v) -> v == null ? 1 : v++);
                if (nanos > 200_000) {
                    times.compute(path, (k, v) -> v == null ? nanos : v + nanos);
                }
                return result.build();
            };
        }

        private boolean isSchemaTypesFetch(DataFetchingEnvironment env, Object source) {
            String parentPath = env.getExecutionStepInfo().getParent().getPath().getPathWithoutListEnd().toString();
            return "/__schema/types".equals(parentPath) && source instanceof GraphQLNamedType;
        }
    }

    public IntrospectionBenchmark() {
        String largeSchema = BenchmarkUtils.loadResource("large-schema-4.graphqls");
        GraphQLSchema graphQLSchema = SchemaGenerator.createdMockedSchema(largeSchema);
        graphQL = GraphQL.newGraphQL(graphQLSchema)
                //.instrumentation(countingInstrumentation)
                .build();
    }

    public static void main(String[] args) {
        IntrospectionBenchmark introspectionBenchmark = new IntrospectionBenchmark();
//        while (true) {
//            long then = System.currentTimeMillis();
//            ExecutionResult er = introspectionBenchmark.benchMarkIntrospection();
//            long ms = System.currentTimeMillis() - then;
//            System.out.println("Took " + ms + "ms");
//        }

        introspectionBenchmark.benchMarkIntrospection();

        Map<String, Long> counts = sortByValue(introspectionBenchmark.countingInstrumentation.counts);
        Map<String, Long> times = sortByValue(introspectionBenchmark.countingInstrumentation.times);

        System.out.println("Counts");
        counts.forEach((k, v) -> System.out.printf("C %-70s : %020d\n", k, v));
        System.out.println("Times");
        times.forEach((k, v) -> System.out.printf("T %-70s : %020d\n", k, v));


    }

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public ExecutionResult benchMarkIntrospection() {
        return graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY);
    }

}
