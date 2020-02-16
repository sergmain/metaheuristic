class ResourceCodeV5:
    def __init__(self, name=None, input=None, output_sourcing='launchpad'):
        if input is None:
            self.__input = {}
        else:
            self.__input = input
        self.__name = name
        self.output_sourcing = output_sourcing

class Variable:
    def __init__(self, name=None, input=None, output_sourcing='launchpad'):
        if input is None:
            self.__input = {}
        else:
            self.__input = input
        self.__name = name
        self.output_sourcing = output_sourcing

class SnippetV1:
    def __init__(self, code, params=None):
        self.__code = code
        self.__params = params


class ExperimentV1:
    def __init__(self, code):
        self.__code = code


def exec_snippet(snippet, data):
    pass


def exec_experiment(experiment, data):
    pass


def mh_permute(data):
    pass


def mh_dictionary_to_resources_code(input):
    pass


