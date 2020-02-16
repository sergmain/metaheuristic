from metaheuristic import *

snippet_assembly = SnippetV1(code='serg.assembly.dataset:1.3', params='A')
snippet_dataset = Snippet(code='serg.assembly.dataset:1.3', params='A B')
snippet_feature_1 = Snippet(code='serg.feature.standard:1.5', params='CLUSTER')
snippet_feature_2 = Snippet(code='serg.feature.standard:1.5', params='MATRIX')

input = Variable(name='input codes', input={'input': '$$initial_pool_code$$'})
input_as_dictionary = dictionary_to_resources_code(input)

call_data_assembly = Variable(name='assembly raw file', input=input_as_dictionary)
assembled_raw = exec_snippet(snippet=snippet_assembly, data=call_data_assembly)

call_data_dataset = Variable(name='dataset processing', input={'assembled_raw': assembled_raw})
dataset_processing = exec_snippet(snippet=snippet_dataset, data=call_data_dataset)

call_data_feature = Variable(name='dataset processing', input={'assembled_raw': assembled_raw})

feature_processing_1 = exec_snippet(snippet=snippet_feature_1, data=call_data_feature)
feature_processing_2 = exec_snippet(snippet=snippet_feature_2, data=call_data_feature)

snippet_feature_2 = Snippet(code='mh.permute-variables-and-hyper-params:1.5')
features = exec_snippet([feature_processing_1, feature_processing_2])

