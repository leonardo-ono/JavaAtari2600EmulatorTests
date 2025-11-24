// src/extension.ts
import * as vscode from 'vscode';
import * as path from 'path';
import { DebugAdapterDescriptorFactory, DebugAdapterExecutable, DebugAdapterDescriptor } from 'vscode';
import { MyDebugAdapter } from './MyDebugAdapter';
import { DebugSession, InitializedEvent, TerminatedEvent, OutputEvent } from '@vscode/debugadapter';

export function activate(context: vscode.ExtensionContext) {
    console.log('My Debugger extension is now active!');

    // Registrar o debugger
    context.subscriptions.push(
        vscode.debug.registerDebugAdapterDescriptorFactory(
            'java-atari2600-debugger', // mesmo tipo que você colocou no package.json
            new MyDebugAdapterDescriptorFactory()
        )
    );


}

export function deactivate() {
    console.log('My Debugger extension is now deactivated');
}

class MyDebugAdapterDescriptorFactory implements DebugAdapterDescriptorFactory {
    createDebugAdapterDescriptor(
        session: vscode.DebugSession,
        executable?: DebugAdapterExecutable
    ): DebugAdapterDescriptor {
        // Caminho absoluto para o adaptador
        const extensionPath = "F:/leo_hd_d_backup/vscode_dev/java-atari2600-debugger-extension";
        const adapterPath = path.join(extensionPath, 'out', 'MyDebugAdapter.js');

        const command = 'node';
        const args = [adapterPath];

        //if (executable) {
            
        //    return executable;
        //}

      return new DebugAdapterExecutable(command, args);
      //return new DebugAdapterExecutable('node', ['out/MyDebugAdapter.js']);
    }
}
