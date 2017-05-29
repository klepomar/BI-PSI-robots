////////////////////////////////////////////////////////////////////
//There are several changes made from the version I submitted as the one supposed to be graded.
//Most of them are just syntactical or adding comments and don;t add any functionality.
//I also added comments marked like this to places where bigger changes were made with explanations why I made theese changes.
////////////////////////////////////////////////////////////////////


package cz.goodhoko.tcp;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Coordinates {

    public Integer x;
    public Integer y;



    Coordinates(Integer x, Integer y){
        this.x = x;
        this.y = y;
    }



    Boolean atDestination(){
        return x == 0 && y == 0;
    }



    public static Coordinates parse(String in){
        if(in.length() > 10)
            return null;

        Pattern p = Pattern.compile("OK\\s(-?\\d+)\\s(-?\\d+)");
        Matcher m = p.matcher(in);
        if (!m.matches()) {
            return null;
        }

        return new Coordinates(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }



    @Override
    public String toString(){
        return "(" + x + ", " + y + ")";
    }
}
package cz.goodhoko.tcp;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Scanner;

public class Handler implements Runnable{
    Socket socket;
    DataOutputStream out;
    Scanner in;
    StateMachine stateMachine;



    Handler(Socket socket) throws Exception{
        this.socket = socket;
        this.stateMachine = new StateMachine();
        this.out = new DataOutputStream(socket.getOutputStream());
        this.in = new Scanner(new BufferedReader(new InputStreamReader(socket.getInputStream()))).useDelimiter("\\r\\n");
    }



    public void run(){
        try{
            //send login prompt
            out.writeBytes(stateMachine.getNextResponse("").response);
            out.flush();
       
            while (true) {
                socket.setSoTimeout(stateMachine.getTimeout());
                Response response = null;

                if(in.hasNext() && !in.hasNext(".*\\z")){
                    //recieved whole message -> next tick of the statemachine
                    response = stateMachine.getNextResponse(in.next());
                }else{
                    //recieved only part of the message -> give it to the statemachine to check if it is already too long, if not, we'll wait for the rest
                    response = stateMachine.preValidate(in.next());
                }
                System.out.println(response);
                if (!response.response.equals("")) {
                    out.writeBytes(response.response);
                    out.flush();
                }
                if (response.closeAfter) {
                    System.out.println("closed by the stateMachine");
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }

        close();
    }



    private void close(){
        try {
            System.out.println("Closing connection");
            socket.close();
            out.close();
            in.close();
        }catch (Exception e){
            System.out.println("Can't close all resources: " + e);
        }
    }
}
package cz.goodhoko.tcp;

import java.io.IOException;
import java.net.*;

public class Main {

    public static void main(String[] args) {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(6655);
        }
        catch (IOException e) {
            System.out.println("Can't open socket: " + e);
            return;
        }
        System.out.println("Waiting for robots at " + socket.getLocalSocketAddress());
        while(true){
            Socket clientSocket = null;
            try {
                clientSocket = socket.accept();
            } catch (IOException e) {
                System.out.println("Can't accept connection: " + e);
                return;
            }
            System.out.println("Accepted connection from " + clientSocket.getInetAddress());
            Handler handler = null;
            try{
                handler = new Handler(clientSocket);
            }catch(Exception e){
                System.out.println("Can't initialize Handler: " + e);
            }

            new Thread(handler).start();
        }
    }
}


package cz.goodhoko.tcp;


public enum Orientation {
    NORTH("^"),
    EAST(">"),
    SOUTH("v"),
    WEST("<"){
        @Override
        public Orientation next() {
            return values()[0];
        };
    };

    private final String text;

    Orientation(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

    public Orientation next() {
        return values()[ordinal() + 1];
    }
}
package cz.goodhoko.tcp;

public class Response {
    public Boolean closeAfter;
    public String response;



    Response(Boolean closeAfter, String response){
        this.closeAfter = closeAfter;
        this.response = response;
    }



    Response(String response){
        this.closeAfter = false;
        this.response = response;
    }



    @Override
    public String toString(){
        return "RESPONSE: " + closeAfter + " : " + StateMachine.unEscapeString(response);
    }
}
package cz.goodhoko.tcp;

public class Robot {
    private Coordinates coordinates;
    private Orientation orientation;
    private String name;



    Robot(String name){
        this.name = name;
    }



    public Boolean validatePassword(Integer password){
        Integer val = 0;
        for (int i = 0; i < name.length(); i++){
            val += name.charAt(i);
        }

        return password.equals(val);
    }



    public void setCoordinates(Coordinates coordinates){
        this.coordinates = coordinates;
    }



    public Boolean getNextMove(){ //0 -> move, 1 -> rotate
        if(coordinates.x > 0){
            return orientation != Orientation.WEST;
        }
        if(coordinates.x < 0){
            return orientation != Orientation.EAST;
        }
        if(coordinates.y > 0){
            return orientation != Orientation.SOUTH;
        }
        if(coordinates.y < 0){
            return orientation != Orientation.NORTH;
        }

        //just in case we are at destination -> we don't want to move
        return true;
    }



    public void rotate(){
        orientation = orientation.next();
    }



    public Boolean determineOrientation(Coordinates newCoordinates){
        if(!coordinates.x.equals(newCoordinates.x) && !coordinates.y.equals(newCoordinates.y))
            return false;
        if(coordinates.x < newCoordinates.x) {
            this.orientation = Orientation.EAST;
            return true;
        }
        if(coordinates.x > newCoordinates.x) {
            this.orientation = Orientation.WEST;
            return true;
        }
        if(coordinates.y < newCoordinates.y) {
            this.orientation = Orientation.NORTH;
            return true;
        }
        if(coordinates.y > newCoordinates.y) {
            this.orientation = Orientation.SOUTH;
            return true;
        }

        return false;
    }



    @Override
    public String toString(){
        return "ROBOT '" + name + "' " + coordinates + " " + orientation + "\n";
    }
}
package cz.goodhoko.tcp;
////////////////////////////////////////////////////////////////////////////////////////
// removed class Sector from here as it wasn't int use
////////////////////////////////////////////////////////////////////////////////////////



////////////////////////////////////////////////////////////////////////////////////////
// Changed enum State used to represent state of the StateMachine.
//Previously move to next state was implemented in the State class itself and move was done by calling State::next().
//I changed it so state is set explicitly in the StateMachine as it is more self-explanatory
//I also changed names of the state: NO_IDE -> FIRST COORDINATES and FIRST_COORDINATES -> SECOND_COORDINATES
////////////////////////////////////////////////////////////////////////////////////////

public enum State {
    INIT, //initial state
    LOGIN, //wating for username
    PASSWORD, //waiting for password
    FIRST_COORDINATES, //waiting for the first coordinates
    SECOND_COORDINATES, //waiting for the second coordinates
    NAVIGATING, //navigating
    DESTINATION; //wating for message
}
package cz.goodhoko.tcp;

public class StateMachine {
    private static final String SERVER_USER =           "100 LOGIN\r\n";         //VĂ˝zva k zadĂˇnĂ­ uĹľivatelskĂ©ho jmĂ©na
    private static final String SERVER_PASSWORD =       "101 PASSWORD\r\n";      //VĂ˝zva k zadĂˇnĂ­ uĹľivatelskĂ©ho hesla
    private static final String SERVER_MOVE =           "102 MOVE\r\n";          //PĹ™Ă­kaz pro pohyb o jedno pole vpĹ™ed
    private static final String SERVER_TURN_RIGHT =     "104 TURN RIGHT\r\n";    //PĹ™Ă­kaz pro otoÄŤenĂ­ doprava
    private static final String SERVER_PICK_UP =        "105 GET MESSAGE\r\n";   //PĹ™Ă­kaz pro vyzvednutĂ­ zprĂˇvy
    private static final String SERVER_OK =             "200 OK\r\n";            //KladnĂ© potvrzenĂ­
    private static final String SERVER_LOGIN_FAILED =   "300 LOGIN FAILED\r\n";  //ChybnĂ© heslo
    private static final String SERVER_SYNTAX_ERROR =   "301 SYNTAX ERROR\r\n";  //ChybnĂˇ syntaxe zprĂˇvy
    private static final String SERVER_LOGIC_ERROR =    "302 LOGIC ERROR\r\n";   //ZprĂˇva odeslanĂˇ ve ĹˇpatnĂ© situaci

    private static final String CLIENT_RECHARGING =     "RECHARGING";            //Robot se zaÄŤal dobĂ­jet a pĹ™estal reagovat na zprĂˇvy.
    private static final String CLIENT_FULL_POWER =     "FULL POWER";            //Robot doplnil energii a opÄ›t pĹ™Ă­jĂ­mĂˇ pĹ™Ă­kazy.

    private static final Integer TIMEOUT =              1;
    private static final Integer TIMEOUT_RECHARGING = 	5;

    private State state;
    private Boolean charging;
    private String buffer;
    private Robot robot;



    StateMachine(){
        this.state = State.INIT;
        this.charging = false;
        this.buffer = "";  //I changed this because the value in previous version wasn't actualy in use
        this.robot = null;
    }



    public Integer getTimeout(){
        return 1000 * (charging ? TIMEOUT_RECHARGING : TIMEOUT);
    }



    public Response getNextResponse(String in){
        System.out.println("getNextResponse: '" + StateMachine.unEscapeString(in) + "'");
        in = buffer.concat(in);
        buffer = "";

        if(charging){
            return validateCharging(in);
        }
        if(in.equals(CLIENT_RECHARGING)){
            charging = true;
            return new Response("");
        }

        switch (state){
            case INIT:
                state = State.LOGIN;
                return new Response(SERVER_USER);
            case LOGIN:
                return validateLogin(in);
            case PASSWORD:
                return validatePassword(in);
            case FIRST_COORDINATES:
                return firstCoordinates(in);
            case SECOND_COORDINATES:
                return secondCoordinates(in);
            case NAVIGATING:
                return getNextMove(in);
            case DESTINATION:
                return retrieveMessage(in);
        }
        
        return new Response("");
    }



    private Response validateCharging(String in) {
        if(!in.equals(CLIENT_FULL_POWER))
            return new Response(true, SERVER_LOGIC_ERROR);
        charging = false;
        return new Response("");
    }



    private Response validateLogin(String in) {
        if(!checkLength(in.length())){
            System.out.println("ERROR: Too long username: '" + unEscapeString(in) + "'");
            return new Response(true, SERVER_SYNTAX_ERROR);
        }

        robot = new Robot(in);
        state = State.PASSWORD;
        System.out.println("Username validated: '" + unEscapeString(in) + "'");
        return new Response(SERVER_PASSWORD);
    }



    private Response validatePassword(String in) {
        if(!checkLength(in.length())){
            System.out.println("ERROR: Too long password: '" + unEscapeString(in) + "'");
            return new Response(true, SERVER_SYNTAX_ERROR);
        }

        Integer password = 0;
        try{
            password = Integer.parseInt(in);
        }catch(Exception e){
            System.out.println("ERROR: can't parse Integer from password: '" + unEscapeString(in) + "'");
            return new Response(true, SERVER_SYNTAX_ERROR);
        }

        if(!robot.validatePassword(password)){
            System.out.println("ERROR: wrong password: '" + unEscapeString(in) + "'");
            return new Response(true, SERVER_LOGIN_FAILED);
        }

        state = State.FIRST_COORDINATES;
        System.out.println("Password validated");
        return new Response(SERVER_OK + SERVER_MOVE);
    }



    private Response firstCoordinates(String in) {
        Coordinates coordinates = Coordinates.parse(in);
        if(coordinates == null){
            System.out.println("ERROR: can't parse coordinates: '" + unEscapeString(in) + "'");
            return new Response(true, SERVER_SYNTAX_ERROR);
        }
        if(coordinates.atDestination()){
            System.out.println("Reached destination");
            this.state = State.DESTINATION;
            return new Response(SERVER_PICK_UP);
        }

        System.out.println("Got first coordinates of the robot: " + coordinates);
        robot.setCoordinates(coordinates);
        this.state = State.SECOND_COORDINATES;
        return new Response(SERVER_MOVE);
    }



    private Response secondCoordinates(String in) {
        Coordinates coordinates = Coordinates.parse(in);
        if(coordinates == null){
            System.out.println("ERROR: can't parse coordinates: '" + unEscapeString(in) + "'");
            return new Response(true, SERVER_SYNTAX_ERROR);
        }
        if(coordinates.atDestination()){
            this.state = State.DESTINATION;
            System.out.println("Reached destination");
            return new Response(SERVER_PICK_UP);
        }
        if(!robot.determineOrientation(coordinates)){
            System.out.println("Robot didn't move. Let's do it again.");
            return new Response(SERVER_MOVE);
        }

        System.out.println("Determined orientation, proceeding to navigating");
        robot.setCoordinates(coordinates);
        state = State.NAVIGATING;
        if(robot.getNextMove()){
            robot.rotate();
            return new Response(SERVER_TURN_RIGHT);
        }

        return new Response(SERVER_MOVE);
    }



    private Response getNextMove(String in) {
        Coordinates coordinates = Coordinates.parse(in);
        if(coordinates == null){
            System.out.println("ERROR: can't parse coordinates: '" + unEscapeString(in) + "'");
            return new Response(true, SERVER_SYNTAX_ERROR);
        }
        if(coordinates.atDestination()){
            this.state = State.DESTINATION;
            System.out.println("Reached destination");
            return new Response(SERVER_PICK_UP);
        }

        System.out.println("robot is now on: " + coordinates);
        robot.setCoordinates(coordinates);
        if(robot.getNextMove()){
            robot.rotate();
            return new Response(SERVER_TURN_RIGHT);
        }

        return new Response(SERVER_MOVE);
    }



    private Response retrieveMessage(String in) {
        if(!checkLength(in.length())){
            System.out.println("ERROR: too long message: '" + unEscapeString(in) + "'");
            return new Response(true, SERVER_SYNTAX_ERROR);
        }

        System.out.println("MESSAGE RETRIEVED: " + unEscapeString(in));
        return new Response(true, SERVER_OK);
    }


    //helper function for debuging
    public static String unEscapeString(String s){
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<s.length(); i++)
            switch (s.charAt(i)){
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                case '\r': sb.append("\\r"); break;
                // ... rest of escape characters
                default: sb.append(s.charAt(i));
            }
        return sb.toString();
    }


    //accepts incomplete message, checks if it is not too long and saves it to the buffer
    public Response preValidate(String in){
        if(checkLength(buffer.length() + in.length())){
            buffer = buffer.concat(in);
            return new Response("");
        }
        return new Response(true, SERVER_SYNTAX_ERROR);
    }


    //checks if availabe is not more than lenght limit of the current expected message
    public boolean checkLength(int available) {

        ////////////////////////////////////////////////////////////////////
        //I removed the case for charging because it wasn't actualy in use
        ////////////////////////////////////////////////////////////////////
        switch (state){
            case LOGIN:
            case DESTINATION:
                return available <= 98;
            case PASSWORD:
                return available <= 5;
            case FIRST_COORDINATES:
            case SECOND_COORDINATES:
            case NAVIGATING:
                return available <= 10;
        }

        return true;
    }
}
