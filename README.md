This project is a Java-based application designed to control and monitor the Eclipse SUMO (Simulation of Urban MObility) engine in real-time. Developed as part of a university project at the Frankfurt University of Applied Sciences, it provides a high-level interface for traffic analysis, adaptive signal control, and dynamic vehicle interaction.
-----------------------
Team Members

    Sherwin Nourinfar

    David Eslami Amirabadi

    Cagla Aktas

    Ha Cuong Tran

    Huu Van Duc
----------------------
Core Features

Real-Time TraCI Integration: Bi-directional communication with SUMO using the TraCI API, allowing for live data extraction and command injection.

Adaptive Traffic Light Control: An intelligent control loop that monitors lane occupancy and waiting times to dynamically adjust signal phases, reducing congestion.

High-Performance Map Rendering: A custom JavaFX-based canvas capable of rendering large-scale networks (e.g., Frankfurt am Main) with smooth zooming and panning.

Dynamic Vehicle Spawning: Interface to inject vehicles with specific types, colors, and speeds. Includes a "Stress Test" mode to evaluate network capacity.

Performance Optimization: Implementation of a batch-processing system for TraCI queries to ensure UI responsiveness even in complex urban scenarios.

Data Export: Functionality to generate simulation reports in CSV and PDF formats, including speed history and travel time distributions.
------------------------

Technical Challenges Solved
Thread Synchronization

To prevent race conditions between the JavaFX UI thread and the simulation backend, a robust locking mechanism (traciLock) was implemented. This ensures that only one thread communicates with the SUMO server at a time, preventing socket corruption.
Network Scaling (Frankfurt Case Study)

Simulating large-scale networks like Frankfurt presented significant performance hurdles. We optimized the network topology (joining junctions within 45m) and throttled traffic light logic updates to prevent "Thread Starvation," ensuring the simulation remains interactive under heavy load.
Dynamic Routing

Vehicles are equipped with rerouting devices, allowing them to autonomously find alternative paths when encountering congestion caused by traffic signal adjustments or high-density traffic injections.
---------------------------------------
Prerequisites

Java JDK 17 or higher.

Eclipse SUMO (Version 1.21.0 or higher recommended).

    Environment Variables: The SUMO bin directory must be added to your system's PATH variable so that sumo and sumo-gui can be called from the terminal.

Maven for dependency management.
----------------------------------

Installation & Usage

Clone the Repository:


Open in IDE: Import the project as a Maven project to automatically resolve dependencies (PDFBox, TraCI4J).

Run the Application: Execute src/controller/MainLauncher.java.

Simulation Steps:

Press Play to initialize the TraCI connection.

Use the panels on the right for controls

--------------------------------

Project Structure

src/controller/: Manages the main simulation loop, TraCI connection lifecycle, and event handling.

src/model/: Contains data wrappers for Vehicle, TrafficLight, and Edge objects, handling state synchronization.

src/view/: JavaFX UI components, including the custom map canvas and dashboard.

sumofiles/: Contains optimized .net.xml files, route definitions, and configuration files.
