Real-Time Traffic Simulation with Java

This project is a Java-based application that controls the Eclipse SUMO traffic simulator in real-time using the TraaS (TraCI as a Service) API. It was developed as part of a university project at Frankfurt University of Applied Sciences.

Team Members

    Sherwin Nourinfar

    David Eslami Amirabadi

    Cagla Aktas

    Ha Cuong Tran

    Huu Van Duc
------ 
Prerequisites

To run this application, you need:

    Java JDK 17 or higher.

    Eclipse SUMO (Version 1.24 recommended).

        Ensure the SUMO bin folder is added to your system environment variables so the command "sumo" works in the terminal.
        If not: go into environment variables and add the path
------
Installation

    Clone this repository to your computer.

    Open the project in your IDE.

    Add the TraaS.jar to your project dependencies.
    If cloned, it should already be in your dependencies
------
How to Run

    Navigate to the file src/controller.Main.java.

    Run the main method.

    The application will automatically:

        Start a SUMO server instance.

        Load the configuration file sumofiles/test123.sumocfg.

        Run the simulation for 5000 steps.

        Log traffic statistics (vehicle count, average speed) to the console.

        Alternatively, in the beginning of our main, replace the string "sumo" with "sumo-gui"
        and the pre existing sumo gui will open and you can simulate.
------
Project Structure

    src/: Contains the Java source code.

        controller.Main.java: The entry point of the application.

        model/: Contains classes for Vehicles, Traffic Lights, and Edges.

    sumofiles/: Contains the SUMO network and route files.

    lib/: Contains external libraries (TraaS.jar).
------
Features (Milestone 1)

    Connection to SUMO via TraaS.

    Object-Oriented Wrappers for simulation objects.

    Basic traffic analysis logging (Vehicle count, Average speed).

    Clean console logging configuration.
