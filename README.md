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

        Open our GUI and be ready to use upon pressing play
------
Project Structure

    src/: Contains the Java source code.

        controller/: The entry point of the application and the one handling data between model and view.

        model/: Contains classes for Vehicles, Traffic Lights, and Edges.

        view/: Contains classes for the GUI.

    sumofiles/: Contains the SUMO network and route files.

    lib/: Contains external libraries (TraaS.jar).

