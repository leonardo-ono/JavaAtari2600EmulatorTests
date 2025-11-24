// src/MyDebugAdapter.ts
import { DebugSession, InitializedEvent, TerminatedEvent, OutputEvent, StoppedEvent, ContinuedEvent, Source, StackFrame, Handles } from '@vscode/debugadapter';
import { DebugProtocol } from '@vscode/debugprotocol';

import { spawn, ChildProcessWithoutNullStreams } from 'child_process';
import * as net from 'net';

interface AtariLaunchRequestArguments extends DebugProtocol.LaunchRequestArguments {
    program: string; // caminho para o binário/ROM
}

function sleep(delay: any) {
    var start = new Date().getTime();
    while (new Date().getTime() < start + delay);
}

export class MyDebugAdapter extends DebugSession {

    private client: net.Socket | null = null;

    constructor() {
        super();
        this.setDebuggerLinesStartAt1(true);
        this.setDebuggerColumnsStartAt1(true);
    }

    private emulatorProcess?: ChildProcessWithoutNullStreams;
    private programName: string = "";

    async launch(args: any) {
        this.sendEvent(new OutputEvent("Launching ROM:" + args.program + "\n"));
        
        // start emulator
        this.programName = args.program;
        this.emulatorProcess = spawn('cmd.exe', ['/c', 'java_atari_2600', args.program]);
        
        // TODO
        sleep(2000);

        // Cria a conexão TCP com localhost:8080
        this.client = net.createConnection({ host: '127.0.0.1', port: 8080 }, () => {
            console.log('Connected to debug server!');
            this.sendEvent(new OutputEvent('Connected to debug server!\n'));
        });

        this.client.on('data', (data) => {
            console.log('Received from server:', data.toString());
            this.sendEvent(new OutputEvent('Received from server: ' + data.toString() + '\n'));

            // reached breakpoint
            if (data.toString().startsWith("reached_breakpoint")) {
                //"reached_breakpoint 82 c1 00 d8 ff f053 84"
                this.updateInfo(data.toString().replace("reached_breakpoint", ""));
                this.sendEvent(new OutputEvent('reached breakpoint!\n'));
                this.sendEvent(new StoppedEvent('pause', 1)); // threadId = 1
            }        
        });
       
        this.client.on('end', () => {
            console.log('Server disconnected!');
            this.sendEvent(new OutputEvent('Server disconnected!\n'));
            this.sendEvent(new TerminatedEvent());
        });

        this.client.on('error', (err) => {
            console.error('Connection error:', err);
            this.sendEvent(new OutputEvent('Connection error!\n'));
            this.sendEvent(new TerminatedEvent());
        });
    }

    sendToServer(message: string) {
        if (this.client) {
            this.client.write(message + '\n');
        }
    }

    // Função que envia um comando ao servidor e aguarda a resposta
    async sendCommand(command: string): Promise<string> {
        return new Promise((resolve, reject) => {
            const onData = (data: Buffer) => {
                this.client!.removeListener("data", onData); // evita múltiplos eventos
                resolve(data.toString().trim());
            };
            this.client!.on("data", onData);
            this.client!.write(command + "\n");
        });
    }

    /*
     * Registrar as capacidades do seu debug adapter (ex.: suporta stepIn, breakpoints, variables, etc.) enviando de volta um InitializeResponse.
     * Fazer qualquer inicialização básica que não dependa ainda de um processo de depuração em execução — por exemplo, preparar variáveis internas, criar objetos de conexão, etc.
     */
    protected override initializeRequest(
        response: DebugProtocol.InitializeResponse,
        args: DebugProtocol.InitializeRequestArguments
    ): void {
        this.sendResponse(response);
        this.sendEvent(new OutputEvent('Debugging initialized!\n'));
        this.sendEvent(new InitializedEvent());
    }

    /*
     * Muitas implementações conectam-se ao processo de depuração real quando recebem a request launch ou attach.
     * initialize serve só para negociação de capacidades com o VS Code.
     */    
    protected override launchRequest(
        response: DebugProtocol.LaunchResponse,
        args: AtariLaunchRequestArguments //DebugProtocol.LaunchRequestArguments
    ): void {

        // start connection with Java Atari 2600 Debugger Server
        this.launch(args);

        this.sendEvent(new OutputEvent('Debugging launched!\n'));
        this.sendResponse(response);
    }

    protected override threadsRequest(response: DebugProtocol.ThreadsResponse) {
        // For our purposes, only one thread is used.
        response.body = {
            threads: [
                { id: 1, name: 'Main Thread' }
            ]
        };
        this.sendResponse(response);
        this.sendEvent(new OutputEvent('threads requested!\n'));
    }


    protected override disconnectRequest(
        response: DebugProtocol.DisconnectResponse,
        args: DebugProtocol.DisconnectArguments
    ): void {
        this.sendEvent(new OutputEvent('Debugging terminated!\n'));
        this.sendResponse(response);
    }

    // chamada quando o VSCode envia pause
    protected override async pauseRequest(response: DebugProtocol.PauseResponse, args: DebugProtocol.PauseArguments) {
        // send pause command to java atari 2600 debugger server
        //this.sendToServer("pause");
        const newLineStr = await this.sendCommand("pause");
        this.updateInfo(newLineStr);

        this.sendEvent(new OutputEvent('pause requested!\n'));
        this.sendEvent(new StoppedEvent('pause', 1)); // threadId = 1
        this.sendResponse(response);
    }

    // debug info test
    private currentLine: number = 0;
    private ra: string = "";
    private rx: string = "";
    private ry: string = "";
    private rs: string = "";
    private rpc: string = "";
    private rp: string = "";

    private updateInfo(data: string) {
        const parts = data.trim().split(/\s+/);
                
        if (parts.length < 7) {
            console.error("invalid debug info:", data);
            return;
        }

        // extract debug info
        this.currentLine = Number(parts[0]);
        this.ra = parts[1];
        this.rx = parts[2];
        this.ry = parts[3];
        this.rs = parts[4];
        this.rpc = parts[5];
        this.rp = parts[6];
    }

    protected override async nextRequest(response: DebugProtocol.NextResponse, args: DebugProtocol.NextArguments) {
        
        // send pause command to java atari 2600 debugger server
        //this.sendToServer("step");
        const newLineStr = await this.sendCommand("step");
        this.updateInfo(newLineStr);
        
        this.sendEvent(new OutputEvent('next requested! newLineStr=' + newLineStr + '\n'));

        this.sendResponse(response);
        this.sendEvent(new StoppedEvent('pause', 1)); // threadId = 1
    }

    protected override continueRequest(response: DebugProtocol.ContinueResponse, args: DebugProtocol.ContinueArguments) {
        // send resume command to java atari 2600 debugger server
        this.sendToServer("resume");

        // resume execution
        this.sendResponse(response);
        this.sendEvent(new ContinuedEvent(1)); // threadId = 1
        this.sendEvent(new OutputEvent('continue requested!\n'));
    }

    // informa a linha que foi pausado
    protected stackTraceRequest(
        response: DebugProtocol.StackTraceResponse,
        args: DebugProtocol.StackTraceArguments
    ) {

        // TODO how to find out the extension of source file?
        let a26File = this.programName.replace(/\.bin$/, ".a");

        // Um exemplo: thread única, linha 10 do arquivo test.js
        const frame = new StackFrame(
            1,                       // id da stack frame
            '',                  // nome da função
            //new Source('test.dasm', 'F:/leo_hd_d_backup/vscode_dev/debug_debug/test.dasm'),
            new Source(a26File, a26File),
            this.currentLine,                       // linha (começa em 1)
            1                         // coluna
        );

        response.body = {
            stackFrames: [frame],
            totalFrames: 1
        };
        this.sendResponse(response);
    }

    private breakpoints: { [path: string]: DebugProtocol.Breakpoint[] } = {};

    protected override setBreakPointsRequest(
        response: DebugProtocol.SetBreakpointsResponse,
        args: DebugProtocol.SetBreakpointsArguments
    ) {
        const path = args.source?.path;
        const clientBreakpoints = args.breakpoints || [];

        if (!path) {
            // Se não tem path, retorna breakpoint "não verificado"
            response.body = {
                breakpoints: (args.breakpoints || []).map(bp => ({ verified: false, line: bp.line }))
            };
            this.sendResponse(response);
            return;
        }

        // Limpa breakpoints antigos para este arquivo
        this.breakpoints[path] = [];

        // Registra novos breakpoints
        const actualBreakpoints = clientBreakpoints.map(bp => {
            const verified = true; // neste exemplo, assumimos que todos podem ser atingidos
            const b: DebugProtocol.Breakpoint = { verified, line: bp.line };
            this.breakpoints[path].push(b);
            return b;
        });

        // send breakpoints to server
        const breakpointLines = clientBreakpoints.map(bp => bp.line).join(" ");
        this.sendEvent(new OutputEvent("Breakpoints lines: " + breakpointLines + " \n"));
        this.sendToServer("breakpoints " + breakpointLines);

        response.body = { breakpoints: actualBreakpoints };

        //response.body = {
        //    breakpoints: clientBreakpoints.map(bp => ({ verified: false, line: bp.line, message: 'Line is not executable' }))
        //};

        this.sendResponse(response);
        this.sendEvent(new OutputEvent('Breakpoint request ... ' + actualBreakpoints + " \n"));
    }
 
    protected scopesRequest(
        response: DebugProtocol.ScopesResponse,
        args: DebugProtocol.ScopesArguments
    ): void {
        response.body = {
            scopes: [
                {
                    name: 'Registers',
                    variablesReference: this._registerHandles.create('registers'),
                    expensive: false
                }
            ]
        };
        this.sendResponse(response);
    }

    private randomHex(): string {
        const n = Math.floor(Math.random() * 0x10000); // número entre 0 e 0xFFFF
        return '0x' + n.toString(16).padStart(4, '0'); // converte para hex e preenche com zeros
    }

    private _registerHandles = new Handles<string>();

    protected override variablesRequest(
        response: DebugProtocol.VariablesResponse,
        args: DebugProtocol.VariablesArguments
    ): void {
        this.sendEvent(new OutputEvent('Variables request ... \n'));

        const id = this._registerHandles.get(args.variablesReference);
        if (id === 'registers') {
            // cpu registers
            response.body = {
                variables: [
                    { name: 'A', value: this.ra, variablesReference: 0 },
                    { name: 'X', value: this.rx, variablesReference: 0 },
                    { name: 'Y', value: this.ry, variablesReference: 0 },
                    { name: 'S', value: this.rs, variablesReference: 0 },
                    { name: 'PC', value: this.rpc, variablesReference: 0 },
                    { name: 'P', value: this.rp, variablesReference: 0 }
                ]
            };
        } else {
            response.body = { variables: [] };
        }
        this.sendResponse(response);
    }

}

DebugSession.run(MyDebugAdapter);