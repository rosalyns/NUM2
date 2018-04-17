package client.progressview;

import javax.swing.JFrame;
import javax.swing.JProgressBar;

public class GUI extends JFrame implements View {
	/**
	 * 
	 */
	private static final long serialVersionUID = -603632627785886496L;

	JProgressBar pbar;
	String title;

	static final int MY_MINIMUM = 0;
	static final int MY_MAXIMUM = 100;

	public GUI(String title) {
		this.title = title;
	}

	@Override
	public void run() {
		pbar = new JProgressBar();
	    pbar.setMinimum(MY_MINIMUM);
	    pbar.setMaximum(MY_MAXIMUM);
	    // add to JPanel
	    add(pbar);
		
		JFrame frame = new JFrame(title);
	    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	    frame.setSize(300,50);
	    frame.add(pbar);
	    frame.setVisible(true);
	}

	@Override
	public void updateProgress(int percentage) {
		pbar.setValue(percentage);
	}
	
	
}
