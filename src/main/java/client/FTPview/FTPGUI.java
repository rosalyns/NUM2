package client.FTPview;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import client.Client;

public class FTPGUI extends JFrame implements FTPView, ActionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7376568300974120629L;
	private Client client;

	private JButton uploadBut;
	private JButton downloadBut;
	private JComboBox<String> filebox;

	public FTPGUI(Client client) {
		this.client = client;
	}

	@Override
	public void run() {
		this.client.askForFiles();
	}

	@Override
	public void actionPerformed(ActionEvent event) {
		if (event.getSource() == downloadBut) {
			File file = new File((String) filebox.getSelectedItem());
			client.downloadFile(file);
		} else if (event.getSource() == uploadBut) {
			JFileChooser fileChooser = new JFileChooser();
			fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
			int result = fileChooser.showOpenDialog(this);
			if (result == JFileChooser.APPROVE_OPTION) {
				File selectedFile = fileChooser.getSelectedFile();
				client.uploadFile(selectedFile);
			}
		}
	}

	@Override
	public void showFilesOnServer(String[] files) {
		JFrame guiFrame = new JFrame("FTP");
		// make sure the program exits when the frame closes
		guiFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		guiFrame.setSize(300, 150);
		// This will center the JFrame in the middle of the screen
		guiFrame.setLocationRelativeTo(null);

		final JPanel comboPanel = new JPanel();
		JLabel comboLbl = new JLabel("Files to download:");
		filebox = new JComboBox<String>(files);
		comboPanel.add(comboLbl);
		comboPanel.add(filebox);

		final JPanel subPanel = new JPanel();
		downloadBut = new JButton("Download");
		downloadBut.addActionListener(this);

		uploadBut = new JButton("Upload");
		uploadBut.addActionListener(this);
		subPanel.add(uploadBut);
		subPanel.add(downloadBut);

		guiFrame.add(comboPanel, BorderLayout.CENTER);
		guiFrame.add(subPanel, BorderLayout.SOUTH);
		// make sure the JFrame is visible
		guiFrame.setVisible(true);
	}

}
