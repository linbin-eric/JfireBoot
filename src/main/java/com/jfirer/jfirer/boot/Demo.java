package com.jfirer.jfirer.boot;

import com.jfirer.baseutil.STR;

import javax.naming.NameNotFoundException;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Demo
{
    public static void main(String[] args)
    {
        Pattern pattern = Pattern.compile("\\d+");
        File dir = new File("/Users/linbin/Downloads/火影忍者疾风传 221-700");
        for (File file : dir.listFiles())
        {
            String name = file.getName();
            Matcher matcher = pattern.matcher(name);
            matcher.find();
            String num = matcher.group();
            int    index   = name.indexOf("话");
            System.out.println(STR.format("S1E{}.{}",num,name.substring(index+1)));
            file.renameTo(new File(file.getParentFile(),STR.format("S1E{}.{}",num,name.substring(index+1))));
        }
    }
}
