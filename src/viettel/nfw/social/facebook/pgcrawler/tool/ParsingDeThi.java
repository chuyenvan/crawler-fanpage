package viettel.nfw.social.facebook.pgcrawler.tool;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import vn.itim.engine.util.FileUtils;

/**
 *
 * @author duongth5
 */
public class ParsingDeThi {

	public static void main(String[] args) throws IOException {

		Set<Exam> exams = new HashSet<>();
		String dir = "C:\\Users\\Duong\\Documents\\ThiChinhTri\\parsing";
		File folder = new File(dir);
		File[] listOfFiles = folder.listFiles();
		for (File file : listOfFiles) {
			if (file.isFile()) {
				exams.addAll(parsing(file));
			} else if (file.isDirectory()) {
				System.out.println("Directory " + file.getAbsolutePath());
			}
		}
		
		StringBuilder sb = new StringBuilder();
		int i = 0;
		for (Exam exam : exams) {
			i++;
			sb.append("####").append(i);
			sb.append("\n\n");
			sb.append(exam.toString());
			sb.append("\n");
		}
		FileUtils.write(new File("dapanthi.txt"), sb.toString());
	}

	public static Set<Exam> parsing(File file) throws IOException {
		Set<Exam> exams = new HashSet<>();
//		StringBuilder sb = new StringBuilder();
		Document doc = Jsoup.parse(file, "UTF-8");
		Element questionIdEl = doc.getElementById("Exam_Question");
		if (questionIdEl != null) {
			Elements questionBlocks = questionIdEl.select("tbody > tr[id^=shortcut-index]");
			if (!questionBlocks.isEmpty()) {
				for (Element questionBlock : questionBlocks) {
					// question content
					Element qContent = questionBlock.select("div.Content > div[id^=dnn_ctr503]").get(0);
					String questionContent = qContent.text();

					// answer choices
					Elements answers = questionBlock.select("div.Content > div#divSecond").get(0).select("tbody > tr");
					StringBuilder sb = new StringBuilder();
					for (Element answer : answers) {
						sb.append(answer.text());
						sb.append("\n");
					}
					String answersContent = sb.toString();
					// correct answer
					Element correctAns = questionBlock.select("div.Content > div.CorrectAnswer").get(0);
					String correctAnswer = correctAns.text();
					exams.add(new Exam(questionContent, answersContent, correctAnswer));
				}
			}
		}
		System.out.println(exams.size());
		return exams;
	}

	private static class Exam {

		public String questionContent;
		public String answers;
		public String correctAns;

		public Exam(String questionContent, String answers, String correctAns) {
			this.questionContent = questionContent;
			this.answers = answers;
			this.correctAns = correctAns;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Exam)) {
				return false;
			}
			if (obj == this) {
				return true;
			}

			Exam rhs = (Exam) obj;
			return new EqualsBuilder().
				// if deriving: appendSuper(super.equals(obj)).
				append(questionContent, rhs.questionContent).
				append(correctAns, rhs.correctAns).
				isEquals();
		}

		@Override
		public int hashCode() {
			return new HashCodeBuilder(17, 31). // two randomly chosen prime numbers
				// if deriving: appendSuper(super.hashCode()).
				append(questionContent).
				append(correctAns).
				toHashCode();
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(questionContent);
			sb.append("\n\n");
			sb.append(answers);
			sb.append("\n");
			sb.append(correctAns);
			sb.append("\n");
			return sb.toString();
		}

	}
}
