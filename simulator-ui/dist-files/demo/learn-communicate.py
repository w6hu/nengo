N=60
D=1

import nef
import nef.templates.learned_termination as learning
import nef.templates.gate as gating
import random

from ca.nengo.math.impl import FourierFunction
from ca.nengo.model.impl import FunctionInput
from ca.nengo.model import Units

random.seed(27)

net=nef.Network('Learn Communication') #Create the network object

# Create input and output populations.
A=net.make('pre',N,D) #Make a population with 60 neurons, 1 dimensions
B=net.make('post',N,D) #Make a population with 60 neurons, 1 dimensions

# Create a random function input.
input=FunctionInput('input',[FourierFunction(
    .1, 10,.5, 12)],
    Units.UNK) #Create a white noise input function .1 base freq, max 
               #freq 10 rad/s, and RMS of .5; 12 is a seed
net.add(input) #Add the input node to the network
net.connect(input,A)

# Create a modulated connection between the 'pre' and 'post' ensembles.
learning.make(net,errName='error', N_err=100, preName='pre', postName='post',
    rate=5e-7) #Make an error population with 100 neurons, and a learning 
               #rate of 5e-7

# Set the modulatory signal.
net.connect('pre', 'error')
net.connect('post', 'error', weight=-1)

# Add a gate to turn learning on and off.
net.make_input('switch',[0]) #Create a controllable input function 
                             #with a starting value of 0
gating.make(net,name='Gate', gated='error', neurons=40,
    pstc=0.01) #Make a gate population with 100 neurons, and a postsynaptic 
               #time constant of 10ms
net.connect('switch', 'Gate')

# Add another non-gated error population running in direct mode.
actual = net.make('actual error', 1, 1, 
    mode='direct') #Make a population with 1 neurons, 1 dimensions, and 
                   #run in direct mode
net.connect(A,actual)
net.connect(B,actual,weight=-1)

net.add_to_nengo()
