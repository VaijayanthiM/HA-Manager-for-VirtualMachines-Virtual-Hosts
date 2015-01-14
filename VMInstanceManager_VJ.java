package com.vmware.vim25.mo.samples;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Random;

import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;

public class VMInstanceManager_VJ {





	private static Boolean checkHostStatus(String hostip) throws IOException 
	{
		Boolean isReachable = false;
		Runtime r = Runtime.getRuntime();
		Process pingProcess = r.exec("ping " + hostip);
		String pingResult = "";
		BufferedReader in = new BufferedReader(new InputStreamReader(
				pingProcess.getInputStream()));
		String inputLine;
		while ((inputLine = in.readLine()) != null) 
		{
			//System.out.println(inputLine);
			pingResult += inputLine;
		}
		// Ping fails
		if (pingResult.contains("Request timed out")) 
		{
			System.out.println("**********************IP Not Found*************************");
			isReachable = false;
		} 
		// Ping Success 			
		else 
		{
			isReachable = true;
			System.out.println("*****************************IP is live*************************");
		}
		return isReachable;
	}

	private static void migrateVM(ServiceInstance si, VirtualMachine vm, String vmname, String newhostname)
			throws Exception{
		Folder rootFolder = si.getRootFolder();
		HostSystem newHost = (HostSystem) new InventoryNavigator(rootFolder).searchManagedEntity("HostSystem", newhostname);
		ComputeResource cr = (ComputeResource) newHost.getParent();

		String[] checks = new String[] {"cpu", "software"};
		HostVMotionCompatibility[] vmcs =
				si.queryVMotionCompatibility(vm, new HostSystem[] 
						{newHost},checks );

		String[] comps = vmcs[0].getCompatibility();
		if(checks.length != comps.length)
		{
			System.out.println("CPU/software NOT compatible. Exit.");

			return;
		}

		Task task = vm.migrateVM_Task(cr.getResourcePool(), newHost,
				VirtualMachineMovePriority.highPriority, 
				VirtualMachinePowerState.poweredOff);

		if(task.waitForMe()==Task.SUCCESS)
		{
			System.out.println("VMotioned!");
		}
		else
		{
			System.out.println("VMotion failed!");
			TaskInfo info = task.getTaskInfo();
			System.out.println(info.getError().getFault());
		}

	}

	private static void cloneVM(VirtualMachine vm, String vmname, String clonename) throws Exception
	{
		VirtualMachineCloneSpec cloneSpec = 
				new VirtualMachineCloneSpec();
		cloneSpec.setLocation(new VirtualMachineRelocateSpec());
		cloneSpec.setPowerOn(false);
		cloneSpec.setTemplate(false);

		Task task = vm.cloneVM_Task((Folder) vm.getParent(), clonename, cloneSpec);
		System.out.println("Launching the VM clone task. " +
				"Please wait ...");

		String status = task.waitForMe();
		if(status==Task.SUCCESS)
		{
			System.out.println("VM got cloned successfully.");
		}
		else
		{
			System.out.println("Failure -: VM cannot be cloned");
		}


	}

	private static void vmPrintConfig(VirtualMachine vm) 
	{
		if (vm != null) 
		{
			VirtualMachineConfigInfo vmConfigInfo = vm.getConfig();
			VirtualMachineCapability vmCapability = vm.getCapability();
			// Print Configuration of the Virtual machine
			System.out.println("*************************************************");
			System.out.println("| Virtual Machine Name        : " 
					+ vm.getName() + "*");
			System.out.println("| Virtual Machine CPUs        : "
					+ vmConfigInfo.getHardware().getNumCPU() + "*");
			System.out.println("| Virtual Machine Version     : " 
					+ vmConfigInfo.getVersion() + "*");
			System.out.println("| Virtual Machine Memory      : "
					+ vmConfigInfo.getHardware().getMemoryMB());
			System.out.println("| Guest Machine Name          : "
					+ vmConfigInfo.getGuestFullName() + "*");
			System.out.println("| Multiple snapshot supported : "
					+ vmCapability.isMultipleSnapshotsSupported() + "*");
			System.out.println("*************************************************");

		}
		else
			System.out.println("No VM found to report statistics of!");
	}

	static void listSnapshots(VirtualMachine vm)
	{
		if(vm==null)
		{
			return;
		}
		VirtualMachineSnapshotInfo snapInfo = vm.getSnapshot();
		VirtualMachineSnapshotTree[] snapTree = 
				snapInfo.getRootSnapshotList();
		printSnapshots(snapTree);
	}

	static void printSnapshots(
			VirtualMachineSnapshotTree[] snapTree)
	{
		for (int i = 0; snapTree!=null && i < snapTree.length; i++) 
		{
			VirtualMachineSnapshotTree node = snapTree[i];
			System.out.println("Snapshot Name : " + node.getName());           
			VirtualMachineSnapshotTree[] childTree = 
					node.getChildSnapshotList();
			if(childTree!=null)
			{
				printSnapshots(childTree);
			}
		}
	}


	private static void vmTakeSnapshots(ServiceInstance si, VirtualMachine vm, String vmname)
	{

		//please change the following three depending your op
		String snapshotname = vmname+"_snapshot";
		String desc = "A description for sample snapshot";
		boolean removechild = true;

		System.out.println("Taking Snapshots for VM: " + vmname);



		if(vm==null)
		{
			System.out.println("No VM " + vmname + " found");

			return;
		}
		try
		{

			Task task = vm.removeAllSnapshots_Task();      
			if(task.waitForMe()== Task.SUCCESS) 
			{
				System.out.println("Removed all snapshots");
			}


			task = vm.createSnapshot_Task(
					snapshotname, desc, false, false);
			if(task.waitForMe()==Task.SUCCESS)
			{
				System.out.println("Snapshot was created.");
			}
		}
		catch (SnapshotFault e) 
		{
			e.printStackTrace();
		} 
		catch (TaskInProgress e) 
		{
			e.printStackTrace();
		}
		catch (InvalidState e) 
		{
			e.printStackTrace();
		}
		catch (RuntimeFault e) 
		{
			e.printStackTrace();
		}
		catch (RemoteException e) 
		{
			e.printStackTrace();
		}





	}

	static VirtualMachineSnapshot getSnapshotInTree(
			VirtualMachine vm, String snapName)
	{
		if (vm == null || snapName == null) 
		{
			return null;
		}

		VirtualMachineSnapshotTree[] snapTree = 
				vm.getSnapshot().getRootSnapshotList();
		if(snapTree!=null)
		{
			ManagedObjectReference mor = findSnapshotInTree(
					snapTree, snapName);
			if(mor!=null)
			{
				return new VirtualMachineSnapshot(
						vm.getServerConnection(), mor);
			}
		}
		return null;
	}

	static ManagedObjectReference findSnapshotInTree(
			VirtualMachineSnapshotTree[] snapTree, String snapName)
	{
		for(int i=0; i <snapTree.length; i++) 
		{
			VirtualMachineSnapshotTree node = snapTree[i];
			if(snapName.equals(node.getName()))
			{
				return node.getSnapshot();
			} 
			else 
			{
				VirtualMachineSnapshotTree[] childTree = 
						node.getChildSnapshotList();
				if(childTree!=null)
				{
					ManagedObjectReference mor = findSnapshotInTree(
							childTree, snapName);
					if(mor!=null)
					{
						return mor;
					}
				}
			}
		}
		return null;
	}


	private static void vmRevertSnapshots(ServiceInstance si, VirtualMachine vm, String vmname)
	{

		//please change the following three depending your op
		String snapshotname = vmname+"_snapshot";
		String desc = "A description for sample snapshot";


		System.out.println("Reverting Snapshots for VM/Host: " + vmname);




		if(vm==null)
		{
			System.out.println("No VM/Host " + vmname + " found");

			return;
		}
		try
		{
			listSnapshots(vm);
			VirtualMachineSnapshot vmsnap = getSnapshotInTree(vm, snapshotname);
			if(vmsnap!=null)
			{

				Task task = vmsnap.revertToSnapshot_Task(null);
				if(task.waitForMe()==Task.SUCCESS)
				{
					System.out.println("Reverted to snapshot:"  + snapshotname);
				}
			}
			
			else
			{
			System.out.println("No such snapshot name exists for this VM!");
			}


		}
		catch (SnapshotFault e) 
		{
			e.printStackTrace();
		} 
		catch (TaskInProgress e) 
		{
			e.printStackTrace();
		}
		catch (InvalidState e) 
		{
			e.printStackTrace();
		}
		catch (RuntimeFault e) 
		{
			e.printStackTrace();
		}
		catch (RemoteException e) 
		{
			e.printStackTrace();
		}





	}





	private static Boolean checkVMStatus(String vmip, String hostip, VirtualMachine vm,
			ServiceInstance si) throws Exception 
			{
		System.out.println("\n*******************Inside CheckVMStatus:"+vmip+"******************");
		Boolean isReachable = false;
		String newhostIP;

		String vmname=vm.getName();

		if (vmip==null)/*if vmip=null, vm is switched off*/ 
		{


			if(checkHostStatus(hostip))
			{
				System.out.println("*****************************Reverting to VM's previous snapshot**************************");
				String cloneName=vmname+"_clone";
				//cloneVM(vm, vmname, cloneName);
				vmRevertSnapshots(si, vm, vmname);
				Task task = vm.powerOnVM_Task(null);
				if(task.waitForMe()==Task.SUCCESS)
				{
					System.out.println(vmname + " powered on*********************************");
				}

			}
			else
			{
				System.out.println("****************************Unhandled connection!**************");

				
				System.out.println("*****************************There is some problem with the host! Reverting host's snapshots***********************");
				URL url1 = new URL("https://130.65.132.14/sdk");
				ServiceInstance si1 = new ServiceInstance(url1, "administrator", "12!@qwQW", true);
				Folder rootFolder1 = si1.getRootFolder();
				String name1 = rootFolder1.getName();
				System.out.println("root:" + name1 );
				ManagedEntity host1=new InventoryNavigator(rootFolder1).searchManagedEntity("VirtualMachine", "t17-vHost01-cum3-lab2_.133.52");
				ManagedEntity host2=new InventoryNavigator(rootFolder1).searchManagedEntity("VirtualMachine", "t17-vHost01-cum3-lab1_.133.51");

				VirtualMachine hs=(VirtualMachine)host2;
				String hostname=hs.getName();

				System.out.println("********************host name: " + hostname+"********************");


				
					if(!checkHostStatus("130.65.133.51"))
					{
						System.out.println("*******************Reverting to host's previous snapshots!****************");
						vmRevertSnapshots(si1, hs, hostname);
						Task task = hs.powerOnVM_Task(null);
						if(task.waitForMe()==Task.SUCCESS)
						{
							System.out.println("**********************"+hostname + " powered on*********************************");
						}
						
						Thread.sleep(120000L);
						
						String vm_ip=vm.getGuest().getIpAddress();
						System.out.println("VM_IP now is changed to :" + vm_ip);
						VirtualMachineRuntimeInfo vmr=vm.getRuntime();
						VirtualMachineConnectionState vmcs=vmr.connectionState;
						VirtualMachinePowerState vmps=vmr.powerState;
						
						System.out.println(vmcs.toString()+vm.getGuest().getGuestState()+vmps);
											if((vmps.toString()=="poweredOff")||(vmcs.toString()=="disconnected"))
											{
												
												Task task1=vm.powerOnVM_Task(null);
												if(task1.waitForMe()==Task.SUCCESS)
												{
													System.out.println("**********************"+vm_ip + " powered on*********************************");
												}
												vmRevertSnapshots(si, vm, vmname);
												
											}
											System.out.println(vmcs.toString()+vm.getGuest().getGuestState()+vmps);
						
						
						
					}

					

			
			}

		} 
		// Ping Success 	

		/*condition when VM is still running, but host is turned OFF*/
		else if(!checkHostStatus(vmip))
		{
			System.out.println("\n*****************VM is still running, vHost is switched off!*********************");
			System.out.println("*****************************There is some problem with the host! Reverting host's snapshots***********************");
			URL url1 = new URL("https://130.65.132.14/sdk");
			ServiceInstance si1 = new ServiceInstance(url1, "administrator", "12!@qwQW", true);
			Folder rootFolder1 = si1.getRootFolder();
			String name1 = rootFolder1.getName();
			System.out.println("root:" + name1 );
			ManagedEntity host1=new InventoryNavigator(rootFolder1).searchManagedEntity("VirtualMachine", "t17-vHost01-cum3-lab2_.133.52");
			ManagedEntity host2=new InventoryNavigator(rootFolder1).searchManagedEntity("VirtualMachine", "t17-vHost01-cum3-lab1_.133.51");

			VirtualMachine hs=(VirtualMachine)host2;
			String hostname=hs.getName();

			System.out.println("********************host name: " + hostname+"********************");


			
				if(!checkHostStatus("130.65.133.51"))
				{
					System.out.println("*******************Reverting to host's previuos snapshots!****************");
					vmRevertSnapshots(si1, hs, hostname);
					Task task = hs.powerOnVM_Task(null);
					if(task.waitForMe()==Task.SUCCESS)
					{
						System.out.println("**********************"+hostname + " powered on*********************************");
					}
					
					Thread.sleep(120000L);
					
					
					
					
					Folder rootFolder = si.getRootFolder();
					
					
					
					//ManagedEntity[] mes = new InventoryNavigator(rootFolder).searchManagedEntities("VirtualMachine");
					ManagedEntity mes = new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine","Team17_VM_VJ_lab1_clone");
					vm = (VirtualMachine) mes;
					String vm_ip=vm.getGuest().getIpAddress();
					System.out.println("VM_IP now is changed to :" + vm_ip);
					VirtualMachineRuntimeInfo vmr=vm.getRuntime();
					VirtualMachineConnectionState vmcs=vmr.connectionState;
					VirtualMachinePowerState vmps=vmr.powerState;
					
					System.out.println(vmcs.toString()+vm.getGuest().getGuestState()+vmps);
										if((vmps.toString()=="poweredOff")||vmcs.toString()=="disconnected")
										{
											//vmRevertSnapshots(si, vm, vmname);
											Task task1=vm.powerOnVM_Task(null);
											if(task1.waitForMe()==Task.SUCCESS)
											{
												System.out.println("**********************"+vm_ip + " powered on*********************************");
											}
											vmRevertSnapshots(si, vm, vmname);
											
										}
										System.out.println(vmcs.toString()+vm.getGuest().getGuestState()+vmps);
					
					
					
				}

				else
				{
					System.out.println("*******************HostIP is fine. Taking snapshots!VM is not up as yet!******************");
					vmTakeSnapshots(si, hs, hostname);


				}

		}
		else 
		{
			isReachable = true;
			System.out.println("*************************VM is live and taking snapshots and statistics of VM****************************");
			vmTakeSnapshots(si, vm, vmname);
			vmPrintConfig(vm);

			System.out.println("\n*****************VM is still running, So taking snapshots of vHost too!!!*********************");
			
			URL url1 = new URL("https://130.65.132.14/sdk");
			ServiceInstance si1 = new ServiceInstance(url1, "administrator", "12!@qwQW", true);
			Folder rootFolder1 = si1.getRootFolder();
			String name1 = rootFolder1.getName();
			System.out.println("root:" + name1 );
			ManagedEntity host1=new InventoryNavigator(rootFolder1).searchManagedEntity("VirtualMachine", "t17-vHost01-cum3-lab2_.133.52");
			ManagedEntity host2=new InventoryNavigator(rootFolder1).searchManagedEntity("VirtualMachine", "t17-vHost01-cum3-lab1_.133.51");

			VirtualMachine hs=(VirtualMachine)host2;
			String hostname=hs.getName();

			System.out.println("********************host name: " + hostname+"********************");


			
				if(!checkHostStatus("130.65.133.51"))
				{
					System.out.println("*******************Reverting to host's previuos snapshots!****************");
					
					Task task = hs.powerOnVM_Task(null);
					if(task.waitForMe()==Task.SUCCESS)
					{
						System.out.println(hostname + " powered on*********************************");
					}
					vmRevertSnapshots(si, hs, hostname);
				}

				else
				{
					System.out.println("*******************HostIP is fine. Taking snapshots!******************");
					vmTakeSnapshots(si, hs, hostname);
					
					


				}

		
		}
		return isReachable;
			}

	private static Boolean createAlarmManager(VirtualMachine vm, ServiceInstance si) throws Exception, DuplicateName
	{	
		AlarmManager am = si.getAlarmManager();
		Folder rootFolder = si.getRootFolder();
		if(vm!=null && am!=null)
		{
			StateAlarmExpression expression = createStateAlarmExpression();
			Random r=new Random();
			int alarmnumber=r.nextInt();
			SendEmailAction methodaction1=createEmailAction();
			AlarmAction emailAction = (AlarmAction) createAlarmTriggerAction(methodaction1);
			AlarmSpec alarmSpec = createAlarmSpec("Alarm-Try", emailAction, expression);
			GroupAlarmAction gaa = new GroupAlarmAction();
			gaa.setAction(new AlarmAction[]{emailAction});
			alarmSpec.setAction(gaa);
			alarmSpec.setExpression(expression);
			alarmSpec.setName("VmPowerStateAlarm_VJ"+alarmnumber);
			alarmSpec.setDescription("Monitor VM state and send email");
			alarmSpec.setEnabled(true);    
			AlarmSetting as = new AlarmSetting();
			as.setReportingFrequency(0); //as often as possible
			as.setToleranceRange(0);
			alarmSpec.setSetting(as);
			try
			{
				am.createAlarm(vm, alarmSpec);
				System.out.println("******************Successfully created Alarm: " + "alarm-try*****************");
				return true;
			}
			catch(RemoteException re)
			{
				re.printStackTrace();
				return false;
			}
		}
		else 
		{
			System.out.println("Either VM is not found or Alarm Manager is not available on this server.");
			return false;
		}
	}

	static StateAlarmExpression createStateAlarmExpression()
	{   
		StateAlarmExpression sae = new StateAlarmExpression();
		sae.setOperator(StateAlarmOperator.isEqual);
		sae.setRed("poweredOff");
		sae.setYellow(null);
		sae.setStatePath("runtime.powerState");
		sae.setType("VirtualMachine");
		return sae;
	}

	static MethodAction createPowerOnAction() 
	{
		MethodAction action = new MethodAction();
		action.setName("PowerOnVM_Task");
		MethodActionArgument argument = new MethodActionArgument();
		argument.setValue(null);
		action.setArgument(new MethodActionArgument[] { argument });
		return action;
	}



	static AlarmTriggeringAction createAlarmTriggerAction(
			Action action) 
	{
		AlarmTriggeringAction alarmAction = 
				new AlarmTriggeringAction();
		alarmAction.setYellow2red(true);
		alarmAction.setAction(action);
		return alarmAction;
	}

	static SendEmailAction createEmailAction() 
	{
		SendEmailAction action = new SendEmailAction();
		action.setToList("sjin@vmware.com");
		action.setCcList("admins99999@vmware.com");
		action.setSubject("Alarm - {alarmName} on {targetName}\n");
		action.setBody("Description:{eventDescription}\n"
				+ "TriggeringSummary:{triggeringSummary}\n"
				+ "newStatus:{newStatus}\n"
				+ "oldStatus:{oldStatus}\n"
				+ "target:{target}");
		return action;
	}

	static AlarmSpec createAlarmSpec(String alarmName, AlarmAction action, AlarmExpression expression) throws Exception 
	{      
		AlarmSpec spec = new AlarmSpec();
		spec.setAction(action);
		spec.setExpression(expression);
		spec.setName(alarmName);
		spec.setDescription("Monitor VM state and send email if VM power's off");
		spec.setEnabled(true);      
		return spec;
	}


	public static void main(String[] args) throws Exception
	{
		long start = System.currentTimeMillis();
		URL url = new URL("https://130.65.133.50/sdk");
		ServiceInstance si = new ServiceInstance(url, "administrator", "12!@qwQW", true);
		long end = System.currentTimeMillis();
		System.out.println("time taken:" + (end-start));
		Folder rootFolder = si.getRootFolder();
		HostProfileManager hostFolder= si.getHostProfileManager();
		String name = rootFolder.getName();
		System.out.println("root:" + name );
		ManagedEntity host=new InventoryNavigator(rootFolder).searchManagedEntity("HostSystem", "130.65.133.51");
		//ManagedEntity[] mes = new InventoryNavigator(rootFolder).searchManagedEntities("VirtualMachine");
		ManagedEntity mes = new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine","Team17_VM_VJ_lab1_clone");


		if(mes==null )
		{
			return;
		}
		if(host==null)

		{
			return;
		}

		HostSystem h=(HostSystem)host;
		System.out.println("host IP: "+ h.getName());
		String hostIP=h.getName();


		//System.out.println(checkHostStatus(h.getName()));
		Thread[] vmhealth=new Thread[10];


		for(int i=0;i<1;i++)
		{
			System.out.println("\n*************************Initial setup for vmname:!****************************");
			VirtualMachine vm = (VirtualMachine) mes;
			String vmname=vm.getName();
			System.out.print(vmname);
			
			HostCapability hc=h.getCapability();
			Boolean in= hc.backgroundSnapshotsSupported;
			Boolean in1=hc.cloneFromSnapshotSupported;

			//System.out.println("Boolean:" + in +in1);
			String vm_ip=vm.getGuest().getIpAddress();
			String vm_gueststatus=vm.getGuest().getGuestState();
			System.out.println("**********************VM IP Address: ************************"+ vm_ip );

			vmhealth[i]=new Thread(new vmHealthMonitor(si, vm, vm_ip, hostIP));
			vmhealth[i].start();
			try {
				vmhealth[i]. join () ;
				System.out.println("Returning from thread "+ i);
			} catch (InterruptedException ie) { }



		}
		si.getServerConnection().logout();
	}

	static class vmHealthMonitor implements Runnable
	{
		ServiceInstance si;
		VirtualMachine vm;
		String vm_ip, host_ip;
		public vmHealthMonitor(ServiceInstance si, VirtualMachine vm, String vm_ip, String host_ip)
		{
			this.si=si;
			this.vm=vm;
			this.vm_ip=vm_ip;
			this.host_ip=host_ip;
		}

		public void run()
		{
			for(int i=0;i<2;i++)
			{
				try{
					System.out.println("****************Checking VM Status!****************");

					VirtualMachineRuntimeInfo vmr=vm.getRuntime();
					VirtualMachineConnectionState vmcs=vmr.connectionState;
					VirtualMachinePowerState vmps=vmr.powerState;
					//System.out.println(vmcs);
					System.out.println(vmcs.toString()+vm.getGuest().getGuestState()+vmps);
					//Boolean alarm=createAlarmManager(vm, si);
					if((vmps.toString()=="poweredOff"))
					{
						System.out.println("********************Checking if alarm is triggered!***********************");
						if(createAlarmManager(vm,si))
							System.out.println("***********************Your VM is switched off and no action is required!*****************************");
					}
					else
						checkVMStatus(vm_ip, host_ip, vm, si);
					Thread.sleep(250000L);
				}catch (InterruptedException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}




