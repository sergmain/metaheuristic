from datetime import datetime

import gc
import numpy
import pandas
import sys
from keras import backend as K
from sklearn.preprocessing import MinMaxScaler


class Logger(object):
    def __init__(self):
        self.terminal = sys.stdout
        self.log = open("logfile.log", "w")

    def write(self, message):
        self.terminal.write(message)
        self.log.write(message)
        self.log.flush()

    def flush(self):
        # this flush method is needed for python 3 compatibility.
        # this handles the flush command by doing nothing.
        # you might want to specify some extra behavior here.
        pass


sys.stdout = Logger()


nb_epoch = None
time_steps = None
batch_size = None
test_size_percent = 0.0
dataset_file_name = None

is_scaler = None
model_file_name = None
seed = None
activation = None


print("time_steps: {}, batch_size: {}, nb_epoch: {}, test_size_percent: {} ".format(time_steps, batch_size, nb_epoch, test_size_percent))
print("dataset file: {}, model file name: {}".format(dataset_file_name, model_file_name))

# fix random seed for reproducibility
numpy.random.seed(seed)

print(str(datetime.now()))


#####

def _load_data(data, n_prev):
    docX = []
    for i in range(len(data) - n_prev):
        docX.append(data[i:i + n_prev])
    alsX = numpy.array(docX)

    return alsX


def train_tst_split(df, dataset_y, shift, time_steps, train_size, test_size):
    last_index = shift + train_size + time_steps
    X_train = _load_data(df[shift:last_index], n_prev=time_steps)
    y_train = numpy.array(dataset_y[shift + time_steps:])

    X_test = _load_data(df[shift+train_size:shift + train_size + test_size + time_steps], n_prev=time_steps)
    if last_index>len(dataset_y):
        y_test = numpy.array([])
    else:
        y_test = numpy.array(dataset_y[last_index:])

    return (X_train, y_train), (X_test, y_test)


######

# load the dataset

dataframe = pandas.read_csv(dataset_file_name, engine='python')
dataset_origin = dataframe.values

ds = dataset_origin
dataset_X = None
dataset_X = dataset_X.astype('float64')

dataset_Y = None
dataset_Y = dataset_Y.astype('float64')


col_number = dataset_X.shape[1]

# normalize the dataset
scaler = MinMaxScaler(feature_range=(0, 1))
if is_scaler:
    dataset_X = scaler.fit_transform(dataset_X)

dataset_len = len(dataset_X)
effective_count = int((dataset_len - time_steps) / batch_size) * batch_size
shift = dataset_len - effective_count

train_size = int(effective_count * (1 - test_size_percent))
train_size = int(train_size / batch_size) * batch_size
test_size = effective_count - train_size
print("col_number: {}, dataset length: {}, effective_count: {}, shift: {}, train_size: {}, test_size: {}".format(col_number, dataset_len, effective_count, shift, train_size, test_size))

(trainX, trainY), (testX, testY) = train_tst_split(dataset_X, dataset_Y, shift, time_steps, train_size, test_size)


model = None

snippet = None

snippet = """
for x in {0}:
    print(x)
"""

exec(snippet)

model.save(model_file_name)


print('Done.')
print(str(datetime.now()))

K.clear_session()
gc.collect()
