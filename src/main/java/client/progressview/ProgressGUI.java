package client.progressview;

import java.awt.event.WindowEvent;
import java.util.Observable;

import javax.swing.JFrame;
import javax.swing.JProgressBar;

public class ProgressGUI extends JFrame implements ProgressView {
	/**
	 * 
	 */
	private static final long serialVersionUID = -603632627785886496L;

	JProgressBar pbar;
	String title;

	static final int MY_MINIMUM = 0;
	static final int MY_MAXIMUM = 100;

	public ProgressGUI(String title) {
		this.title = title;
	}

	@Override
	public void run() {
		pbar = new JProgressBar();
	    pbar.setMinimum(MY_MINIMUM);
	    pbar.setMaximum(MY_MAXIMUM);
	    // add to JPanel
	    this.add(pbar);
		
	    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	    this.setSize(300,50);
	    this.setVisible(true);
	    this.setTitle(this.title);
	}

	@Override
	public void update(Observable o, Object arg) {
		int percentage = (int) arg;
		if (pbar != null) {
			pbar.setValue(percentage);
		}
		
		if (percentage >= 100) {
			this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
		}
	}

	
}
