import nef
import nps

D=5
net=nef.Network('Basal Ganglia') #Create the network object
input=net.make_input('input',[0]*D) #Create a controllable input function 
                                    #with a starting value of 0 for each of D
                                    #dimensions
output=net.make('output',1,D,mode='direct',
    quick=True)  #Make a population with 100 neurons, 5 dimensions, and set 
                 #the simulation mode to direct
nps.basalganglia.make_basal_ganglia(net,input,output,D,same_neurons=False,
    N=50)  #Make a basal ganglia model with 50 neurons per action
net.add_to_nengo()

